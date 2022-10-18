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
import org.kohsuke.github.*;

import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    fw.write("UTCMergedDateTime,Repo,Title,PRNum,PRAuthor,CodingTimeSecs,PickupTimeSecs,"
        + "ReviewTimeSecs,Review1,Review2,Review3,Review4,Review5,Review6\n");
    
    GHPullRequest pullRequest = repo.getPullRequest(7952);
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
      Date prCreatedAtDate = ghPullRequest.getCreatedAt();
      Date mergedAtDate = ghPullRequest.getMergedAt();

      List<GHPullRequestCommitDetail.Commit> allCommits = ghPullRequest.listCommits().toList()
              .stream().map(GHPullRequestCommitDetail::getCommit)
              .sorted(Comparator.comparing(c -> c.getCommitter().getDate()))
              .collect(Collectors.toList());

      List<GHPullRequestReview> allReviews = ghPullRequest.listReviews().toList()
              .stream().filter(i -> STATES_COUNTED_FOR_REVIEW.contains(i.getState()))
              .sorted(Main::compareReviewsByCreatedDate)
              .collect(Collectors.toList());

      Date firstReviewAtDate = allReviews.stream().map(review -> {
        try {
          return review.getCreatedAt();
        } catch (Exception e) {
          LOGGER.error(e);
          return mergedAtDate;
        }
      }).findFirst().orElse(mergedAtDate);

      Date lastCommitBeforeFirstReviewDate = allCommits.stream()
              .map(GHPullRequestCommitDetail.Commit::getCommitter)
              .map(GitUser::getDate)
              .filter(c -> c.getTime() < firstReviewAtDate.getTime())
              .reduce((a,b) -> b).orElse(firstReviewAtDate);

      //Coding finishes at the later of
      // a) the last commit before the review
      // b) when the PR was created
      long codingFinishTimeMillis = Math.max(lastCommitBeforeFirstReviewDate.getTime(), prCreatedAtDate.getTime());

      Date firstCommitAtDate = allCommits.stream()
              .map(GHPullRequestCommitDetail.Commit::getCommitter)
              .map(GitUser::getDate)
              .findFirst().orElse(prCreatedAtDate);

      long codingTimeSecs = (codingFinishTimeMillis - firstCommitAtDate.getTime())/1000L;
      long pickupTimeSecs = (firstReviewAtDate.getTime() - codingFinishTimeMillis)/1000L;
      long reviewTimeSecs = (mergedAtDate.getTime() - firstReviewAtDate.getTime())/1000L;

      String reviewUserNameStr = allReviews.stream().map(review -> {
        try {
          return review.getUser().getName();
        } catch (Exception e) {
          LOGGER.error(e);
          return "ERROR";
        }
      }).collect(Collectors.joining(","));
      
      fw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
          sdf.format(mergedAtDate),
          ghRepository.getName(),
          ghPullRequest.getTitle(),
          ghPullRequest.getNumber(),
          prAuthor.getName(),
          codingTimeSecs,
          pickupTimeSecs,
          reviewTimeSecs,
          reviewUserNameStr));
      
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
