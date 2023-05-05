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

import com.echobox.github.cycletime.analyse.AnalysisCleanup;
import com.echobox.github.cycletime.analyse.OrgAnalyser;
import com.echobox.github.cycletime.analyse.PRAnalyser;
import com.echobox.github.cycletime.analyse.SquadDailyCycleTimeCalculator;
import com.echobox.github.cycletime.data.AnalysedPR;
import com.echobox.github.cycletime.data.AuthorsToSquadsCSVDAO;
import com.echobox.github.cycletime.data.PreferredAuthorNamesCSVDAO;
import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import com.echobox.github.cycletime.enrichment.jira.JIRAEpicWorkTypeEnricher;
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
    
//    performExportAndAnalysis(githubOrg);
//    cleanupAnalysis();
//    aggregateCycleTimes();
    
    buildEpicTypesFromIssueKeys();
  
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

  private static void buildEpicTypesFromIssueKeys() throws Exception  {
    
    String jiraURL = System.getenv("JIRA_URL");
    String jiraLoginEmail = System.getenv("JIRA_EMAIL");
    String jiraLoginAPIToken = System.getenv("JIRA_API_TOKEN");
    
    JIRAEpicWorkTypeEnricher enricher =
        new JIRAEpicWorkTypeEnricher(jiraURL, jiraLoginEmail, jiraLoginAPIToken);
    
    List<String> distinctIssueKeys;
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        true)) {
      List<AnalysedPR> uncleanedPRs = csv.loadAllData();
      distinctIssueKeys = uncleanedPRs.stream()
          .map(pr -> pr.getPrTitle())
          .map(JIRAEpicWorkTypeEnricher::getIssueKeyFromPRTitle)
          .filter(Optional::isPresent).map(Optional::get)
          .distinct().collect(Collectors.toList());
      LOGGER.debug("Loaded " + uncleanedPRs.size() + " PRs");
    }
    
    //Lookup the epic types for each issue key and then save those out
  }
  
}
