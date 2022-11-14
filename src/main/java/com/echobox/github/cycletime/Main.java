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

import com.echobox.github.cycletime.analyse.OrgAnalyser;
import com.echobox.github.cycletime.analyse.PRAnalyser;
import com.echobox.github.cycletime.data.AnalysedPR;
import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import com.echobox.github.cycletime.providers.kohsuke.PullRequestKohsuke;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final TimeZone persistWithTimezone = TimeZone.getTimeZone("UTC");
  private static final String RAW_CSV_FILENAME = "export.csv";
  private static final String SORTED_CSV_FILENAME = "export_sorted_by_mergedate.csv";
  private static final String SORTED_CSV_FILENAME_FILTERED_AUTHORS =
      "export_sorted_by_mergedate_filteredauthors.csv";
  
  private static final List<String> AUTHORS_TO_FILTEROUT = Lists.newArrayList("^dependabot.*");
  
  public static void main(String[] args) throws Exception {

    LOGGER.info("Starting ...");

    //performAnalyse();
    performAggregate();
  
    LOGGER.info("... Done");
  }
  
  private static void performAnalyse() throws Exception {
    //Requires GITHUB_OAUTH=... env variable setting with token
    GitHub github = GitHubBuilder.fromEnvironment().build();
    
    String orgIdentifier = "ebx";
    GHOrganization githubOrg = github.getOrganization(orgIdentifier);
    
    GHRateLimit rateLimitStart = github.getRateLimit();
    LOGGER.debug("Rate limit remaining in current hour window - "
        + rateLimitStart.getCore().getRemaining());
    
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        false)) {
      
      csv.writeCSVHeader();
  
      // 1664582400 Start of October
      // 1661990400 Start of Sept
      long considerOnlyPRsMergedAfterUnixTime = 1664582400;
      long considerOnlyPRsMergedBeforeUnixTime = 1667260800;
  
      analyseEntireOrg(githubOrg, considerOnlyPRsMergedAfterUnixTime,
          considerOnlyPRsMergedBeforeUnixTime, csv);
  
      //analyseSpecificPR(githubOrg, "ebx-linkedin-sdk", 218, fw);
    }
    
    GHRateLimit rateLimitEnd = github.getRateLimit();
    int usedRateLimit =
        rateLimitStart.getCore().getRemaining() - rateLimitEnd.getCore().getRemaining();
    
    LOGGER.debug("Used the following rate limit quota - " + usedRateLimit);
  }
  
  private static void analyseEntireOrg(GHOrganization githubOrg,
      long considerOnlyPRsMergedAfterUnixTime, long considerOnlyPRsMergedBeforeUnixTime,
      PullRequestCSVDAO csv) throws IOException {
    
    OrgAnalyser orgAnalyser = new OrgAnalyser(githubOrg, considerOnlyPRsMergedAfterUnixTime,
        considerOnlyPRsMergedBeforeUnixTime, csv);
    orgAnalyser.analyseOrg();
  }

  private static void analyseSpecificPR(GHOrganization githubOrg, String repoName,
      int prNum, PullRequestCSVDAO csv) throws IOException {
    
    GHRepository repo = githubOrg.getRepository(repoName);
    GHPullRequest pullRequest = repo.getPullRequest(prNum);
    PRAnalyser analyser = new PRAnalyser(repo.getName(), new PullRequestKohsuke(pullRequest));
    analyser.analyse();
    
    csv.writeToCSV(analyser.getAnalysis());
  }

  private static void performAggregate() throws Exception {
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        true)) {
      
      List<AnalysedPR> analysedPRs = csv.loadAllData();
      
      LOGGER.debug("Loaded " + analysedPRs.size() + " PRs");
      
      List<AnalysedPR> sorted = analysedPRs.stream()
          .filter(Main::testAuthorFilterList)
          .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
          .collect(Collectors.toList());
      
      try (PullRequestCSVDAO csvOut = new PullRequestCSVDAO(SORTED_CSV_FILENAME,
          persistWithTimezone, false)) {
        csvOut.writeCSVHeader();
        csvOut.writeToCSV(sorted);
      }
  
      List<AnalysedPR> sortedFilteredAuthors = analysedPRs.stream()
          .filter(pr -> !testAuthorFilterList(pr))
          .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
          .collect(Collectors.toList());
  
      try (PullRequestCSVDAO csvOut = new PullRequestCSVDAO(SORTED_CSV_FILENAME_FILTERED_AUTHORS,
          persistWithTimezone, false)) {
        csvOut.writeCSVHeader();
        csvOut.writeToCSV(sortedFilteredAuthors);
      }
    }
  }
  
  private static boolean testAuthorFilterList(AnalysedPR pr) {
    for (String regex : AUTHORS_TO_FILTEROUT) {
      if (pr.getPrAuthorStr().matches(regex)) {
        return false;
      }
    }
    return true;
  }
}
