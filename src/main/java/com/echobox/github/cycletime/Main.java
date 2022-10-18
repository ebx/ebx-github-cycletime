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

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;

import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
  private static final SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT);
  private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
  
  private static final Set<GHPullRequestReviewState> STATES_COUNTED_FOR_REVIEW =
      Sets.newHashSet(GHPullRequestReviewState.CHANGES_REQUESTED,
          GHPullRequestReviewState.APPROVED, GHPullRequestReviewState.DISMISSED);
  
  public static void main(String[] args) throws Exception {
  
    sdf.setTimeZone(UTC_TZ);
    
    //Requires GITHUB_OAUTH=... env variable setting with token
    GitHub github = GitHubBuilder.fromEnvironment().build();
    GHOrganization ebx = github.getOrganization("ebx");

    GHRateLimit rateLimitStart = github.getRateLimit();
    LOGGER.debug("Rate limit remaining in current hour window - "
        + rateLimitStart.getCore().getRemaining());
 
    GHRepository repo = ebx.getRepository("main");
  
    FileWriter fw = new FileWriter("export.csv");
    fw.write("UTCMergedDateTime,Repo,Title,PRNum,Author,CodingTimeSecs,PickupTimeSecs,"
        + "ReviewTimeSecs,Reviewer1,Reviewer2,Reviewer3,Reviewer4,Reviewer5\n");
    
    GHPullRequest pullRequest = repo.getPullRequest(7962);
    processPR(repo, pullRequest, fw);
  
    //forAllRepos(ebx);

    GHRateLimit rateLimitEnd = github.getRateLimit();
    int usedRateLimit =
        rateLimitStart.getCore().getRemaining() - rateLimitEnd.getCore().getRemaining();
    
    LOGGER.debug("Used the following rate limit quota - " + usedRateLimit);
    fw.close();
  }
  
  private static void forAllRepos(GHOrganization ebx, Writer fw) {
    PagedIterable<GHRepository> ghRepositories = ebx.listRepositories();
    ghRepositories.forEach(ghRepository -> processRepo(ghRepository, fw));
  }
  
  private static void processRepo(GHRepository ghRepository, Writer fw) {
    
    if (!ghRepository.getName().equals("main")) {
      return;
    }
    
    LOGGER.debug(ghRepository.getName());
    
    try {

      PagedIterable<GHPullRequest> prs =
          ghRepository.queryPullRequests().state(GHIssueState.CLOSED)
              .sort(GHPullRequestQueryBuilder.Sort.UPDATED).direction(GHDirection.DESC).list();

      prs.forEach(ghPullRequest -> processPR(ghRepository, ghPullRequest, fw));

    } catch (Exception e) {
      LOGGER.error(e);
    }
  }
  
  private static void processPR(GHRepository ghRepository, GHPullRequest ghPullRequest, Writer fw) {
    try {
  
      if (!ghPullRequest.isMerged()) {
        LOGGER.debug("Skip PR #" + ghPullRequest.getNumber() + " as it's not yet merged.");
        return;
      }
  
      GHUser prAuthor = ghPullRequest.getUser();
  
      List<GHPullRequestCommitDetail.Commit> commits = ghPullRequest.listCommits().toList()
              .stream().map(GHPullRequestCommitDetail::getCommit)
              .sorted(Comparator.comparing(c -> c.getCommitter().getDate()))
              .collect(Collectors.toList());
  
      Date firstCommitAtDate = commits.get(0).getCommitter().getDate();
      
      List<GHPullRequestReview> allReviews = ghPullRequest.listReviews().toList().stream()
        .filter(i -> STATES_COUNTED_FOR_REVIEW.contains(i.getState()))
        .sorted(Main::compareReviewsByCreatedDate).collect(Collectors.toList());
  
      List<String> reviewUsers = allReviews.stream().map(review -> {
          try {
            return review.getUser().getName();
          } catch (Exception e) {
            LOGGER.error(e);
            return "ERROR";
          }
        }).collect(Collectors.toList());
  
      Date createdAtDate = ghPullRequest.getCreatedAt();
      Date mergedAtDate = ghPullRequest.getMergedAt();
      Date firstReviewAtDate = mergedAtDate;
      
      if (!allReviews.isEmpty()) {
        firstReviewAtDate = allReviews.get(0).getCreatedAt();
      }
  
      long codingTimeSecs = (firstReviewAtDate.getTime() - firstCommitAtDate.getTime())/1000L;
      long reviewTimeSecs = (mergedAtDate.getTime() - firstReviewAtDate.getTime())/1000L;
      
      //Needs to be calculated as Max(PRCreatedTime,LastCommitBeforeFirstReview)
      long pickupTimeSecs = 0;

      String reviewersStr = reviewUsers.stream().collect(Collectors.joining(","));
      
      fw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
          sdf.format(mergedAtDate),
          ghRepository.getName(),
          ghPullRequest.getTitle(),
          ghPullRequest.getNumber(),
          prAuthor.getName(),
          codingTimeSecs,
          pickupTimeSecs,
          reviewTimeSecs,
          reviewersStr));
      
      LOGGER.debug(ghPullRequest.getTitle() + " - " + prAuthor.getName());

    } catch (Exception e) {
      LOGGER.error(e);
    }
  }
  
  public static int compareReviewsByCreatedDate(GHPullRequestReview rev1,
      GHPullRequestReview rev2) {
    
    Date rev1Created = new Date(System.currentTimeMillis());
    Date rev2Created = new Date(System.currentTimeMillis());
    
    try {
      rev1Created = rev1.getCreatedAt();
    } catch (Exception e) {
      LOGGER.error("Failed to extract created date from review " + rev1.getNodeId());
    }
    
    try {
      rev2Created = rev2.getCreatedAt();
    } catch (Exception e) {
      LOGGER.error("Failed to extract created date from review " + rev2.getNodeId());
    }

    return rev1Created.compareTo(rev2Created);
  }
}
