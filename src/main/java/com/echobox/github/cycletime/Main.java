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

import com.echobox.github.cycletime.persist.CSVPersist;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.TimeZone;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final TimeZone persistWithTimezone = TimeZone.getTimeZone("UTC");
  
  public static void main(String[] args) throws Exception {

    //Requires GITHUB_OAUTH=... env variable setting with token
    GitHub github = GitHubBuilder.fromEnvironment().build();
    
    String orgIdentifier = "ebx";
    GHOrganization githubOrg = github.getOrganization(orgIdentifier);
    
    GHRateLimit rateLimitStart = github.getRateLimit();
    LOGGER.debug("Rate limit remaining in current hour window - "
        + rateLimitStart.getCore().getRemaining());
    
    Writer fw = new PrintWriter("export.csv");
    CSVPersist csv = new CSVPersist(fw, persistWithTimezone);
    csv.writeCSVHeader();
    
    // 1664582400 Start of October
    // 1661990400 Start of Sept
    long considerOnlyPRsMergedAfterUnixTime = 1664582400;
    long considerOnlyPRsMergedBeforeUnixTime = 1667260800;
  
    analyseEntireOrg(githubOrg, considerOnlyPRsMergedAfterUnixTime,
        considerOnlyPRsMergedBeforeUnixTime, csv);
  
    //analyseSpecificPR(githubOrg, "ebx-linkedin-sdk", 218, fw);
  
    fw.close();
    
    GHRateLimit rateLimitEnd = github.getRateLimit();
    int usedRateLimit =
        rateLimitStart.getCore().getRemaining() - rateLimitEnd.getCore().getRemaining();
    
    LOGGER.debug("Used the following rate limit quota - " + usedRateLimit);
  }
  
  private static void analyseEntireOrg(GHOrganization githubOrg,
      long considerOnlyPRsMergedAfterUnixTime, long considerOnlyPRsMergedBeforeUnixTime,
      CSVPersist csv) {
    
    OrgAnalyser orgAnalyser = new OrgAnalyser(githubOrg, considerOnlyPRsMergedAfterUnixTime,
        considerOnlyPRsMergedBeforeUnixTime, csv);
    orgAnalyser.analyseOrg();
  }

  private static void analyseSpecificPR(GHOrganization githubOrg, String repoName,
      int prNum, CSVPersist csv) throws IOException {
    
    GHRepository repo = githubOrg.getRepository(repoName);
    GHPullRequest pullRequest = repo.getPullRequest(prNum);
    PRAnalyser analyser = new PRAnalyser(repo.getName(), pullRequest);
    analyser.analyse();
    
    csv.writeToCSV(analyser);
  }
  
}
