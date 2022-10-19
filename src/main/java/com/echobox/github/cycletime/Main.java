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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
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
import org.kohsuke.github.GitUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Let's download some github information!
 * @author MarcF
 */
public class Main {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
  private static final SimpleDateFormat fullFormat = new SimpleDateFormat(ISO_FORMAT);
  private static final SimpleDateFormat monthOnlyFormat = new SimpleDateFormat("MM");
  private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
  
  private static final Set<GHPullRequestReviewState> STATES_COUNTED_FOR_REVIEW =
      Sets.newHashSet(GHPullRequestReviewState.CHANGES_REQUESTED, GHPullRequestReviewState.APPROVED,
          GHPullRequestReviewState.DISMISSED);
  
  private static final int MIN_SECS_REQUIRED_BETWEEN_REVIEWS = 15 * 60;
  
  private static final int MAX_SECS_CYCLE_COMPONENT = 30 * 24 * 60 * 60; //30 days
  
  public static void main(String[] args) throws Exception {
    
    fullFormat.setTimeZone(UTC_TZ);
    monthOnlyFormat.setTimeZone(UTC_TZ);
    
    //Requires GITHUB_OAUTH=... env variable setting with token
    GitHub github = GitHubBuilder.fromEnvironment().build();
    GHOrganization ebx = github.getOrganization("ebx");
    
    GHRateLimit rateLimitStart = github.getRateLimit();
    LOGGER.debug(
        "Rate limit remaining in current hour window - " + rateLimitStart.getCore().getRemaining());
    
    Writer fw = new PrintWriter("export.csv");
    fw.write("UTCMergedDateTime,MonthIndex,Repo-PRNum,Title,PRAuthor,CodingTimeSecs,PickupTimeSecs,"
        + "ReviewTimeSecs,Review1,Review2,Review3,Review4,Review5,Review6\n");
    
    // 1664582400 Start of October
    // 1661990400 Start of Sept
    long considerOnlyPRsMergedAfterUnixTime = 1664582400;
    long considerOnlyPRsMergedBeforeUnixTime = 1666164908;
    
    //GHRepository repo = ebx.getRepository("ebx-linkedin-sdk");
    //GHPullRequest pullRequest = repo.getPullRequest(218);
    //writePRDataToFile(repo, pullRequest, fw);
    
    forAllRepos(ebx, considerOnlyPRsMergedAfterUnixTime, considerOnlyPRsMergedBeforeUnixTime, fw);
    
    GHRateLimit rateLimitEnd = github.getRateLimit();
    int usedRateLimit =
        rateLimitStart.getCore().getRemaining() - rateLimitEnd.getCore().getRemaining();
    
    LOGGER.debug("Used the following rate limit quota - " + usedRateLimit);
    fw.close();
  }
  
  private static void forAllRepos(GHOrganization ebx, long considerOnlyPRsMergedAfterUnixTime,
      long considerOnlyPRsMergedBeforeUnixTime, Writer fw) {
    PagedIterable<GHRepository> ghRepositories = ebx.listRepositories();
    ghRepositories.forEach(
        ghRepository -> processRepo(ghRepository, considerOnlyPRsMergedAfterUnixTime,
            considerOnlyPRsMergedBeforeUnixTime, fw));
  }
  
  private static void processRepo(GHRepository ghRepository,
      long considerOnlyPRsMergedAfterUnixTime, long considerOnlyPRsMergedBeforeUnixTime,
      Writer fw) {
    
    LOGGER.info(ghRepository.getName());
    
    try {
      
      Set<Integer> processedPRs = new HashSet<>();
      
      PagedIterator<GHPullRequest> prsByCreated =
          ghRepository.queryPullRequests().state(GHIssueState.CLOSED)
              .sort(GHPullRequestQueryBuilder.Sort.CREATED).direction(GHDirection.DESC).list()
              .iterator();
      
      processPRs(ghRepository, considerOnlyPRsMergedAfterUnixTime,
          considerOnlyPRsMergedBeforeUnixTime, fw, processedPRs, prsByCreated,
          ghPullRequest -> {
            try {
              return ghPullRequest.getCreatedAt();
            } catch (IOException ioe) {
              throw new IllegalStateException(
                  "Failed to get created time of PR " + ghPullRequest.getNumber());
            }
          });
      
      PagedIterator<GHPullRequest> prsByUpdated =
          ghRepository.queryPullRequests().state(GHIssueState.CLOSED)
              .sort(GHPullRequestQueryBuilder.Sort.UPDATED).direction(GHDirection.DESC).list()
              .iterator();
      
      processPRs(ghRepository, considerOnlyPRsMergedAfterUnixTime,
          considerOnlyPRsMergedBeforeUnixTime, fw, processedPRs, prsByUpdated,
          ghPullRequest -> {
            try {
              return ghPullRequest.getUpdatedAt();
            } catch (IOException ioe) {
              throw new IllegalStateException(
                  "Failed to get updated time of PR " + ghPullRequest.getNumber());
            }
          });
      
    } catch (Exception e) {
      LOGGER.error(e);
    }
  }
  
  private static void processPRs(GHRepository ghRepository, long considerOnlyPRsMergedAfterUnixTime,
      long considerOnlyPRsMergedBeforeUnixTime, Writer fw, Set<Integer> processedPRs,
      PagedIterator<GHPullRequest> prs, Function<GHPullRequest, Date> prTimeFunction)
      throws IOException {
    
    while (prs.hasNext()) {
      
      GHPullRequest pr = prs.next();
      
      if (processedPRs.contains(pr.getNumber())) {
        continue;
      }
      
      if (prTimeFunction.apply(pr).getTime() / 1000L < considerOnlyPRsMergedAfterUnixTime) {
        LOGGER.debug("Stopping repo as we are now before stop time.");
        break;
      }
      
      if (!pr.isMerged()) {
        LOGGER.debug("Skip PR #" + pr.getNumber() + " as it's not merged.");
        continue;
      }
      
      long prMergedAtUnixTime = pr.getMergedAt().getTime() / 1000L;
      if (prMergedAtUnixTime >= considerOnlyPRsMergedAfterUnixTime
          && prMergedAtUnixTime < considerOnlyPRsMergedBeforeUnixTime) {
        
        writePRDataToFile(ghRepository, pr, fw);
        processedPRs.add(pr.getNumber());
      }
    }
    
  }
  
  private static void writePRDataToFile(GHRepository ghRepository, GHPullRequest ghPullRequest,
      Writer fw) {
    try {
      
      if (!ghPullRequest.isMerged()) {
        LOGGER.debug("Skip PR #" + ghPullRequest.getNumber() + " as it's not merged.");
        return;
      }
      
      GHUser prAuthor = ghPullRequest.getUser();
      Date prCreatedAtDate = ghPullRequest.getCreatedAt();
      Date mergedAtDate = ghPullRequest.getMergedAt();
      
      List<GHPullRequestCommitDetail.Commit> allCommits =
          ghPullRequest.listCommits().toList().stream().map(GHPullRequestCommitDetail::getCommit)
              .sorted(Comparator.comparing(c -> c.getCommitter().getDate()))
              .collect(Collectors.toList());
      
      List<GHPullRequestReview> allReviews = ghPullRequest.listReviews().toList().stream()
          .filter(i -> STATES_COUNTED_FOR_REVIEW.contains(i.getState()))
          .sorted(Main::compareReviewsByCreatedDate).collect(Collectors.toList());

      //Example PR that has duplicate reviews
      //https://github.com/ebx/ebx-linkedin-sdk/pull/218
      List<GHPullRequestReview> deDuplicatedReviews = new ArrayList<>();
      long lastReviewMillis = 0;
      //Remove any reviews that are too close to the previous one.
      for (GHPullRequestReview review : allReviews) {
        long reviewUnixTimeMillis = review.getCreatedAt().getTime();
        if (reviewUnixTimeMillis > lastReviewMillis + (MIN_SECS_REQUIRED_BETWEEN_REVIEWS * 1000)) {
          deDuplicatedReviews.add(review);
          lastReviewMillis = reviewUnixTimeMillis;
        }
      }

      Date firstReviewAtDate = deDuplicatedReviews.stream().map(review -> {
        try {
          return review.getCreatedAt();
        } catch (Exception e) {
          throw new IllegalStateException("Failed to get createdAt time for PR", e);
        }
      }).findFirst().orElse(mergedAtDate);
      
      Date lastCommitBeforeFirstReviewDate =
          allCommits.stream().map(GHPullRequestCommitDetail.Commit::getCommitter)
              .map(GitUser::getDate).filter(c -> c.getTime() < firstReviewAtDate.getTime())
              .reduce((a, b) -> b).orElse(firstReviewAtDate);
      
      //Coding finishes at the later of
      // a) the last commit before the review
      // b) when the PR was created
      long codingFinishTimeMillis =
          Math.max(lastCommitBeforeFirstReviewDate.getTime(), prCreatedAtDate.getTime());
      
      Date firstCommitAtDate =
          allCommits.stream().map(GHPullRequestCommitDetail.Commit::getCommitter)
              .map(GitUser::getDate).findFirst().orElse(prCreatedAtDate);
      
      long codingTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
          (codingFinishTimeMillis - firstCommitAtDate.getTime()) / 1000L));
      long pickupTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
          (firstReviewAtDate.getTime() - codingFinishTimeMillis) / 1000L));
      long reviewTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
          (mergedAtDate.getTime() - firstReviewAtDate.getTime()) / 1000L));
      
      String reviewUserNameStr = deDuplicatedReviews.stream().map(review -> {
        try {
          return review.getUser();
        } catch (Exception e) {
          throw new IllegalStateException("Failed to get review user", e);
        }
      }).map(Main::getUserName).collect(Collectors.joining(","));

      String repoNum = ghRepository.getName() + "-" + ghPullRequest.getNumber();
      
      fw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
          fullFormat.format(mergedAtDate),
          monthOnlyFormat.format(mergedAtDate),
          repoNum,
          StringEscapeUtils.escapeCsv(ghPullRequest.getTitle()),
          getUserName(prAuthor),
          codingTimeSecs,
          pickupTimeSecs,
          reviewTimeSecs,
          reviewUserNameStr));
      
      fw.flush();
      
      LOGGER.debug(repoNum + " - " + ghPullRequest.getTitle());
      
    } catch (Exception e) {
      LOGGER.error(e);
    }
  }
  
  public static String getUserName(GHUser user) {
    try {
      if (StringUtils.isEmpty(user.getName())) {
        if (user.getLogin() == null) {
          throw new IllegalArgumentException("Failed to get login id for user");
        } else {
          return user.getLogin();
        }
      } else {
        return user.getName();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to determine user name for login " + user.getLogin(),
          e);
    }
  }
  
  public static int compareReviewsByCreatedDate(GHPullRequestReview rev1,
      GHPullRequestReview rev2) {
    
    Date rev1Created;
    Date rev2Created;
    
    try {
      rev1Created = rev1.getCreatedAt();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to extract created date from review "
          + rev1.getNodeId(), e);
    }
    
    try {
      rev2Created = rev2.getCreatedAt();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to extract created date from review "
          + rev2.getNodeId(), e);
    }
    
    return rev1Created.compareTo(rev2Created);
  }
}
