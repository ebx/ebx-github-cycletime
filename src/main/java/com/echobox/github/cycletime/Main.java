/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.echobox.github.cycletime;

import com.chavaillaz.jira.domain.Issue;
import com.echobox.github.cycletime.analyse.AnalysisCleanup;
import com.echobox.github.cycletime.analyse.OrgAnalyser;
import com.echobox.github.cycletime.analyse.PRAnalyser;
import com.echobox.github.cycletime.analyse.SquadDailyCycleTimeCalculator;
import com.echobox.github.cycletime.data.AnalysedPR;
import com.echobox.github.cycletime.data.AuthorsToSquadsCSVDAO;
import com.echobox.github.cycletime.data.ChildIssueKeyToEpicCSVDAO;
import com.echobox.github.cycletime.data.Epic;
import com.echobox.github.cycletime.data.PreferredAuthorNamesCSVDAO;
import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import com.echobox.github.cycletime.enrichment.jira.JIRAEpicWorkTypeEnricher;
import com.echobox.github.cycletime.enrichment.jira.JIRAQueryHelper;
import com.echobox.github.cycletime.providers.kohsuke.PullRequestKohsuke;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private static final ZoneId persistWithTimezone = ZoneId.of("UTC");

  /**
   * A regex list of authors that should be excluded from the primary output
   */
  public static List<String> AUTHORS_TO_FILTEROUT = Lists.newArrayList("^dependabot.*");

  private static final String RAW_CSV_FILENAME = "export.csv";
  private static final boolean APPEND_EXISTING_EXPORT_FILE = true;
  private static final int DEFAULT_EXPORT_DAYS_IF_NO_APPEND = 7;

  private static final String SORTED_CSV_FILENAME = "export_sorted_by_mergedate.csv";
  private static final String SORTED_CSV_FILENAME_FILTERED_AUTHORS =
      "export_sorted_by_mergedate_filteredauthors.csv";
  
  private static final String PREFERRED_AUTHOR_NAMES_CSV = "preferred_author_names.csv";
  private static final String AUTHOR_NAMES_TO_SQUADS_CSV = "author_names_to_squads.csv";
  
  private static final String CYCLE_TIME_SQUAD_NAME_POSTFIX = "_squad_cycle_times_hours.csv";
  
  private static final String ISSUES_TO_EPIC_CSV_FILENAME = "childissues_to_epic.csv";
  
  /**
   * The JIRA JQL that can be used to determine all planned tickets. This is quite implementation
   * specific to Echobox so likely needs improving in a future iteration.
   */
  private static String JIRA_IMPLEMENTATION_TICKETS_JQL = "project in (BM, PW, SL, NL, SDK) AND "
      + "issuetype IN (Implementation, \"Implementation (Spec Gap)\", \"R&D\") and "
      + "status != \"Wont Fix\" AND summary !~ plan AND summary !~ Planning AND "
      + "summary !~ \"create technical\" "
      + "AND created >= \"2023-01-01 00:00\" AND created <= \"2023-12-31 23:59\" ";
  
  /**
   * The JIRA JQL that can be used to determine all R&D tickets. This is quite implementation
   * specific to Echobox so likely needs improving in a future iteration.
   */
  private static String JIRA_R_D_TICKETS_JQL = "project in (BM, PW, NL, SL, SDK) AND "
      + "issuetype IN (\"R&D\") AND status IN (Done, Resolved) "
      + "AND updated >= \"2023-01-01 00:00\" AND updated <= \"2023-12-31 23:59\" ";
  
  public static void main(String[] args) throws Exception {

    LOGGER.info("Starting ...");
  
    String orgIdentifier = System.getenv("ORG_ID");
    if (StringUtils.isEmpty(orgIdentifier)) {
      LOGGER.error("Please provide the github org identifier as an env variable to continue.");
      return;
    }

    //Requires GITHUB_OAUTH=... env variable setting with token
    GitHub github = GitHubBuilder.fromEnvironment().build();

    GHOrganization githubOrg = github.getOrganization(orgIdentifier);

    GHRateLimit rateLimitStart = github.getRateLimit();
    LOGGER.debug("Rate limit remaining in current hour window - "
        + rateLimitStart.getCore().getRemaining());
    
    performExportAndAnalysis(githubOrg);
    cleanupAnalysis();
    aggregateCycleTimes();

    buildEpicTypesFromIssueKeysExport();
  
    GHRateLimit rateLimitEnd = github.getRateLimit();
    int usedRateLimit =
        rateLimitStart.getCore().getRemaining() - rateLimitEnd.getCore().getRemaining();
  
    LOGGER.debug("Done. Used the following rate limit quota - " + usedRateLimit);
  }
  
  private static void performExportAndAnalysis(GHOrganization githubOrg)
      throws Exception {

    boolean exportAlreadyExists = new File(RAW_CSV_FILENAME).exists();
    boolean append = exportAlreadyExists && APPEND_EXISTING_EXPORT_FILE;
    
    try (PullRequestCSVDAO prPersistDAO = new PullRequestCSVDAO(RAW_CSV_FILENAME,
        persistWithTimezone, append)) {
      
      Instant now = Instant.now();
      long considerPRsMergedBeforeUnixTime = now.getEpochSecond();
      long considerPRsMergedAfterUnixTime =
          now.minus(Duration.ofDays(DEFAULT_EXPORT_DAYS_IF_NO_APPEND)).getEpochSecond();
      
      if (append) {
        List<AnalysedPR> existingPRs = prPersistDAO.loadAllData();
  
        Optional<ZonedDateTime> latestExistingMergeDate = existingPRs.stream()
            .map(pr -> pr.getMergedAtDate())
            .sorted(Comparator.reverseOrder())
            .findFirst();
        
        if (latestExistingMergeDate.isPresent()) {
          considerPRsMergedAfterUnixTime =
              latestExistingMergeDate.get().toInstant().getEpochSecond();
        }

      } else {
        prPersistDAO.writeCSVHeader();
      }

      OrgAnalyser orgAnalyser = new OrgAnalyser(githubOrg, considerPRsMergedAfterUnixTime,
          considerPRsMergedBeforeUnixTime, prPersistDAO);
      orgAnalyser.analyseOrg();
    }
  }

  private static void cleanupAnalysis() throws Exception {

    List<AnalysedPR> uncleanedPRs;
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        true)) {
      uncleanedPRs = csv.loadAllData();
      LOGGER.debug("Loaded " + uncleanedPRs.size() + " PRs");
    }

    try (PreferredAuthorNamesCSVDAO preferredAuthorsNamesDAO =
        new PreferredAuthorNamesCSVDAO(PREFERRED_AUTHOR_NAMES_CSV);
        PullRequestCSVDAO cleanedDAOOut = new PullRequestCSVDAO(
            SORTED_CSV_FILENAME, persistWithTimezone, false);
        PullRequestCSVDAO cleanedDAOExcludedAuthorOut = new PullRequestCSVDAO(
            SORTED_CSV_FILENAME_FILTERED_AUTHORS, persistWithTimezone, false)) {
      
      cleanedDAOOut.writeCSVHeader();
      cleanedDAOExcludedAuthorOut.writeCSVHeader();
  
      AnalysisCleanup cleanup = new AnalysisCleanup(uncleanedPRs, preferredAuthorsNamesDAO,
          cleanedDAOOut, cleanedDAOExcludedAuthorOut, AUTHORS_TO_FILTEROUT);
      cleanup.clean();
    }
  }
  
  private static void aggregateCycleTimes() throws Exception {
    
    try (PullRequestCSVDAO csvOut = new PullRequestCSVDAO(SORTED_CSV_FILENAME,
        persistWithTimezone, true);
        AuthorsToSquadsCSVDAO authorsToSquadDAO =
            new AuthorsToSquadsCSVDAO(AUTHOR_NAMES_TO_SQUADS_CSV)) {
  
      List<AnalysedPR> allPRs = csvOut.loadAllData();
      
      SquadDailyCycleTimeCalculator calc = new SquadDailyCycleTimeCalculator(allPRs,
          authorsToSquadDAO, persistWithTimezone, CYCLE_TIME_SQUAD_NAME_POSTFIX);
  
      calc.calculate();
      calc.persistToCSV();
    }
  }

  private static void analyseSpecificPR(GHOrganization githubOrg, String repoName,
      int prNum) throws IOException {
    
    GHRepository repo = githubOrg.getRepository(repoName);
    GHPullRequest pullRequest = repo.getPullRequest(prNum);
    PRAnalyser analyser = new PRAnalyser(repo.getName(), new PullRequestKohsuke(pullRequest));
    analyser.analyse();
  }

  private static void buildEpicTypesFromIssueKeysExport() throws Exception  {
    
    String jiraURL = System.getenv("JIRA_URL");
    String jiraLoginEmail = System.getenv("JIRA_EMAIL");
    String jiraLoginAPIToken = System.getenv("JIRA_API_TOKEN");
    
    if (StringUtils.isEmpty(jiraURL) || StringUtils.isEmpty(jiraLoginEmail)
        || StringUtils.isEmpty(jiraLoginAPIToken)) {
      LOGGER.warn("Not building epic types from issue keys export as jiraURL, jiraLoginEmail or "
          + "jiraLoginAPIToken env variable are missing.");
    }
    
    Set<String> issueKeysToUpdate;
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        true)) {
      List<AnalysedPR> uncleanedPRs = csv.loadAllData();
      issueKeysToUpdate = uncleanedPRs.stream()
          .map(pr -> pr.getPrTitle())
          .map(JIRAEpicWorkTypeEnricher::getIssueKeyFromPRTitle)
          .filter(Optional::isPresent).map(Optional::get)
          .collect(Collectors.toSet());
      LOGGER.debug("Loaded " + uncleanedPRs.size() + " PRs");
    }

    //Get issue keys for
    //  Planed tickets for known projects
    //  R&D tickets
    JIRAQueryHelper queryTool = new JIRAQueryHelper(jiraURL, jiraLoginEmail, jiraLoginAPIToken);
    List<Issue> plannedIssues = queryTool.executeQuery(JIRA_IMPLEMENTATION_TICKETS_JQL);
    List<Issue> rdIssues = queryTool.executeQuery(JIRA_R_D_TICKETS_JQL);

    issueKeysToUpdate.addAll(plannedIssues.stream().map(i -> i.getKey()).collect(
        Collectors.toList()));
    issueKeysToUpdate.addAll(rdIssues.stream().map(i -> i.getKey()).collect(
        Collectors.toList()));
    
    //Load all issue keys previously saved
    boolean writeHeaders = !new File(ISSUES_TO_EPIC_CSV_FILENAME).exists();
    Set<String> issueKeysPreviouslySaved;
    try (ChildIssueKeyToEpicCSVDAO csv =
        new ChildIssueKeyToEpicCSVDAO(ISSUES_TO_EPIC_CSV_FILENAME, true)) {
      if (writeHeaders) {
        csv.writeCSVHeader();
      }
      issueKeysPreviouslySaved = csv.loadAllEpics().keySet();
    }
    
    //Remove known keys
    issueKeysToUpdate.removeAll(issueKeysPreviouslySaved);

    JIRAEpicWorkTypeEnricher enricher =
        new JIRAEpicWorkTypeEnricher(jiraURL, jiraLoginEmail, jiraLoginAPIToken);
    
    //Get the required data for any missing child issue keys
    List<Epic> newEpics = issueKeysToUpdate.stream().parallel()
        .map(childIssueKey -> {
          try {
            Epic epic = enricher.getEpicFromChildIssueKey(childIssueKey);
            LOGGER.debug("Retrieved epic for child key " + childIssueKey);
            return epic;
          } catch (Exception ex) {
            LOGGER.error("** Failed to determine epic for child key " + childIssueKey, ex);
            return new Epic(childIssueKey, "NA", "", "");
          }
        })
        .filter(epic -> epic != null)
        .collect(Collectors.toList());
    
    //Append the new data to the results list
    try (ChildIssueKeyToEpicCSVDAO csv =
        new ChildIssueKeyToEpicCSVDAO(ISSUES_TO_EPIC_CSV_FILENAME, true)) {
      csv.writeToCSV(newEpics);
    }
  }
  
}
