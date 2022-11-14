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
import com.echobox.github.cycletime.data.CycleTimeBucket;
import com.echobox.github.cycletime.data.PreferredAuthorNamesCSVDAO;
import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import com.echobox.github.cycletime.providers.kohsuke.PullRequestKohsuke;
import com.google.common.collect.Lists;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private static final ZoneId persistWithTimezone = ZoneId.of("UTC");
  
  private static final String RAW_CSV_FILENAME = "export.csv";
  private static final String SORTED_CSV_FILENAME = "export_sorted_by_mergedate.csv";
  private static final String SORTED_CSV_FILENAME_FILTERED_AUTHORS =
      "export_sorted_by_mergedate_filteredauthors.csv";
  private static final String PREFERRED_AUTHOR_NAMES_CSV = "preferred_author_names.csv";
  
  private static final List<String> AUTHORS_TO_FILTER_OUT = Lists.newArrayList("^dependabot.*");
  
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
    
    csv.writeToCSV(analyser.getAnalysis(), null);
  }

  private static void performAggregate() throws Exception {
    
    Map<String, String> preferredAuthorNames;
    try (PreferredAuthorNamesCSVDAO names =
        new PreferredAuthorNamesCSVDAO(PREFERRED_AUTHOR_NAMES_CSV)) {
      preferredAuthorNames = names.loadAllPreferredNames();
    }
  
    List<AnalysedPR> analysedPRs;
    try (PullRequestCSVDAO csv = new PullRequestCSVDAO(RAW_CSV_FILENAME, persistWithTimezone,
        true)) {
      analysedPRs = csv.loadAllData();
      LOGGER.debug("Loaded " + analysedPRs.size() + " PRs");
    }

    List<AnalysedPR> sorted = analysedPRs.stream()
        .filter(Main::testAuthorFilterList)
        .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
        .collect(Collectors.toList());
    
    try (PullRequestCSVDAO csvOut = new PullRequestCSVDAO(SORTED_CSV_FILENAME,
        persistWithTimezone, false)) {
      csvOut.writeCSVHeader();
      csvOut.writeToCSV(sorted, preferredAuthorNames);
    }

    List<AnalysedPR> sortedFilteredAuthors = analysedPRs.stream()
        .filter(pr -> !testAuthorFilterList(pr))
        .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
        .collect(Collectors.toList());

    try (PullRequestCSVDAO csvOut = new PullRequestCSVDAO(SORTED_CSV_FILENAME_FILTERED_AUTHORS,
        persistWithTimezone, false)) {
      csvOut.writeCSVHeader();
      csvOut.writeToCSV(sortedFilteredAuthors, preferredAuthorNames);
    }
  
    aggregateCycleTimes(analysedPRs);
  
    LOGGER.debug("Completed aggregation.");
  }
  
  private static void aggregateCycleTimes(List<AnalysedPR> analysedPRs) throws IOException {
    //Test out some cycle times
    List<String> squads = Lists.newArrayList("Ewok", "Ace", "Dragon", "Indigo",
        "Zeus");
    
    ZonedDateTime firstDateTime = analysedPRs.stream().map(AnalysedPR::getMergedAtDate)
            .min(ZonedDateTime::compareTo).get().withZoneSameInstant(persistWithTimezone);
    ZonedDateTime lastDateTime = analysedPRs.stream().map(AnalysedPR::getMergedAtDate)
        .max(ZonedDateTime::compareTo).get().withZoneSameInstant(persistWithTimezone);
    
    ZonedDateTime startAtMidnight = firstDateTime.withHour(0).withMinute(0).withSecond(0);
    ZonedDateTime midnightAtEnd = lastDateTime.withHour(0).withMinute(0).withSecond(0).plusDays(1);
    
    Map<String, List<CycleTimeBucket>> resultMap = new HashMap<>();
    for (String squad : squads) {
      List<CycleTimeBucket> buckets = new ArrayList<>();
      ZonedDateTime currentDateTime = startAtMidnight;
      while (currentDateTime.isBefore(midnightAtEnd)) {
        ZonedDateTime currentPlusOneDay = currentDateTime.plusDays(1);
        buckets.add(new CycleTimeBucket(currentDateTime, currentPlusOneDay));
        currentDateTime = currentPlusOneDay;
      }
      resultMap.put(squad, buckets);
    }
    
    //What about people on multiple squads??
    Map<String, String> authorToSquad = new HashMap<>();
    authorToSquad.put("David Ashton", "Ewok");
    authorToSquad.put("Jack Ellis", "Ewok");
    authorToSquad.put("Kenneth Wong", "Ewok");
    authorToSquad.put("Luke Martin", "Ewok");
    authorToSquad.put("Matt Stobbs", "Ewok");
    authorToSquad.put("Jon Haddow", "Ewok");
    authorToSquad.put("Daniel Martinez-Gatell", "Ewok");
    authorToSquad.put("Guillermo Villalobos", "Ewok");
    authorToSquad.put("Dougal Rea", "Ewok");
    
    for (AnalysedPR pr : analysedPRs) {
      //Find the right author and add values to the correct bucket
      ZonedDateTime mergedDateTime = pr.getMergedAtDate().withZoneSameInstant(persistWithTimezone);
      long daysDiff = ChronoUnit.DAYS.between(startAtMidnight, mergedDateTime);
      String bucketId = authorToSquad.get(pr.getPrAuthorStr());
      if (bucketId == null) {
        continue;
      }
      
      CycleTimeBucket bucket = resultMap.get(bucketId).get((int) daysDiff);
      if (bucket.getStartDateTime().isBefore(pr.getMergedAtDate())
          && bucket.getEndDateTime().isAfter(pr.getMergedAtDate())) {
        bucket.addValues(pr.getCodingTimeSecs(), pr.getPickupTimeSecs(), pr.getReviewTimeSecs());
      } else {
        throw new IllegalStateException("Idiot check - Unexpected bucket selected.");
      }
    }
    
    //Setup headers
    String isoFormat = "yyyy-MM-dd";
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(isoFormat);
    List<String> headers = Lists.newArrayList("", "CodingTimeSecs", "PickupTimeSecs",
        "ReviewTimeSecs");
    
    String hoursFormat = "%.2f";
    FileWriter out = new FileWriter("ewok_cycle_times_hours.csv");
    CSVFormat csvFormat =
        CSVFormat.Builder.create().setHeader(headers.toArray(new String[0])).build();
    try (CSVPrinter printer = new CSVPrinter(out, csvFormat)) {
      String squad = "Ewok";
      
      List<CycleTimeBucket> buckets = resultMap.get(squad);
  
      for (CycleTimeBucket bucket : buckets) {
        
        String codingTimeStr = "";
        if (!bucket.getCodingTimeSecsValues().isEmpty()) {
          double totalInHours = bucket.getAverageCodingTimeSecs().getAsDouble() / 3600;
          codingTimeStr =  String.format(hoursFormat, totalInHours);
        }
        
        String pickupTimeStr = "";
        if (!bucket.getPickupTimeSecsValues().isEmpty()) {
          double totalInHours = bucket.getAveragePickupTimeSecs().getAsDouble() / 3600;
          pickupTimeStr = String.format(hoursFormat, totalInHours);
        }
        
        String reviewTimeStr = "";
        if (!bucket.getReviewTimeSecsValues().isEmpty()) {
          double totalInHours = bucket.getAverageReviewTimeSecs().getAsDouble() / 3600;
          reviewTimeStr = String.format(hoursFormat, totalInHours);
        }
        
        printer.printRecord(dateTimeFormatter.format(bucket.getStartDateTime()),
            codingTimeStr, pickupTimeStr, reviewTimeStr);
      }
    }
  }
  
  private static boolean testAuthorFilterList(AnalysedPR pr) {
    for (String regex : AUTHORS_TO_FILTER_OUT) {
      if (pr.getPrAuthorStr().matches(regex)) {
        return false;
      }
    }
    return true;
  }
}
