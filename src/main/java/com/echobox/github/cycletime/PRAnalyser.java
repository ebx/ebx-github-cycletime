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
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Given a PR, analyses it's content and then writes
 * @author MarcF
 */
public class PRAnalyser {
  
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
  
  private final GHRepository ghRepository;
  private final GHPullRequest ghPullRequest;
  
  private volatile boolean isAnalysed = false;
  
  private Date mergedAtDate;
  private String repoName;
  private int prNum;
  private String prTitle;
  private String prAuthorStr;
  private long codingTimeSecs;
  private long pickupTimeSecs;
  private long reviewTimeSecs;
  private List<String> prReviewedByList;
  
  public PRAnalyser(GHRepository ghRepository, GHPullRequest ghPullRequest) {
    this.ghRepository = ghRepository;
    this.ghPullRequest = ghPullRequest;
  
    ensureCSVTimezonesAreSetToUTC();
  }
  
  private void ensureCSVTimezonesAreSetToUTC() {
    fullFormat.setTimeZone(UTC_TZ);
    monthOnlyFormat.setTimeZone(UTC_TZ);
  }
  
  public void analyse() throws IOException {
    
    repoName = ghRepository.getName();
    prNum = ghPullRequest.getNumber();
    prTitle = ghPullRequest.getTitle();
    prAuthorStr = getSafeUserNameStr(ghPullRequest.getUser());
    mergedAtDate = ghPullRequest.getMergedAt();
  
    List<GHPullRequestCommitDetail.Commit> allCommits = getAllCommits();
  
    List<GHPullRequestReview> deDuplicatedReviews = getValidAndDeduplicatedReviews();
    
    Date firstReviewAtDate = deDuplicatedReviews.stream().map(review -> {
      try {
        return review.getCreatedAt();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to get createdAt time for PR", e);
      }
    }).findFirst().orElse(mergedAtDate);
  
    Date lastCommitBeforeFirstReviewDate = getLastCommitBeforeFirstReviewDate(allCommits,
        firstReviewAtDate);
  
    Date prCreatedAtDate = ghPullRequest.getCreatedAt();
    
    //Coding finishes at the later of
    // a) the last commit before the review
    // b) when the PR was created
    long codingFinishTimeMillis =
        Math.max(lastCommitBeforeFirstReviewDate.getTime(), prCreatedAtDate.getTime());
  
    Date firstCommitAtDate = getFirstCommitAtDate(allCommits).orElse(prCreatedAtDate);
  
    codingTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (codingFinishTimeMillis - firstCommitAtDate.getTime()) / 1000L));
    
    pickupTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (firstReviewAtDate.getTime() - codingFinishTimeMillis) / 1000L));
    
    reviewTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (mergedAtDate.getTime() - firstReviewAtDate.getTime()) / 1000L));
  
    prReviewedByList = deDuplicatedReviews.stream().map(review -> {
      try {
        return review.getUser();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to get review user", e);
      }
    }).map(PRAnalyser::getSafeUserNameStr).collect(Collectors.toList());

    isAnalysed = true;
    LOGGER.debug("Analysed " + repoName + "/" + prNum + " - " + prTitle);
  }
  
  private List<GHPullRequestCommitDetail.Commit> getAllCommits() throws IOException {
    return ghPullRequest.listCommits().toList().stream().map(GHPullRequestCommitDetail::getCommit)
            .sorted(Comparator.comparing(c -> c.getCommitter().getDate()))
            .collect(Collectors.toList());
  }
  
  private Date getLastCommitBeforeFirstReviewDate(List<GHPullRequestCommitDetail.Commit> allCommits,
      Date firstReviewAtDate) {
    return allCommits.stream()
        .map(GHPullRequestCommitDetail.Commit::getCommitter).map(GitUser::getDate)
        .filter(c -> c.getTime() < firstReviewAtDate.getTime())
        .reduce((a, b) -> b)
        .orElse(firstReviewAtDate);
  }
  
  private Optional<Date> getFirstCommitAtDate(List<GHPullRequestCommitDetail.Commit> allCommits) {
    return allCommits.stream()
        .map(GHPullRequestCommitDetail.Commit::getCommitter)
        .map(GitUser::getDate)
        .findFirst();
  }
  
  /**
   * Reviews require some cleanup to ensure we only consider valid ones, as defined in
   * STATES_COUNTED_FOR_REVIEW. We also ignore any reviews that happened within
   * MIN_SECS_REQUIRED_BETWEEN_REVIEWS of each other as this is caused by a duplicate.
   * @return A suitable list of reviews to analyse
   * @throws IOException
   */
  private List<GHPullRequestReview> getValidAndDeduplicatedReviews() throws IOException {
    
    List<GHPullRequestReview> deDuplicatedReviews = new ArrayList<>();
    
    List<GHPullRequestReview> allSortedReviews = ghPullRequest.listReviews().toList().stream()
        .filter(i -> STATES_COUNTED_FOR_REVIEW.contains(i.getState()))
        .sorted(PRAnalyser::compareReviewsByCreatedDate)
        .collect(Collectors.toList());
    
    long lastReviewMillis = 0;
    //Example PR that has duplicate reviews
    //https://github.com/ebx/ebx-linkedin-sdk/pull/218
    //Aim to remove any reviews that are too close to the previous one.
    for (GHPullRequestReview review : allSortedReviews) {
      long reviewUnixTimeMillis = review.getCreatedAt().getTime();
      if (reviewUnixTimeMillis > lastReviewMillis + (MIN_SECS_REQUIRED_BETWEEN_REVIEWS * 1000)) {
        deDuplicatedReviews.add(review);
        lastReviewMillis = reviewUnixTimeMillis;
      }
    }
    
    return deDuplicatedReviews;
  }
  
  /**
   * Write the CSV header for PR analysis
   * @param fw
   */
  public static void writeCSVHeader(Writer fw) throws IOException {
    fw.write("UTCMergedDateTime,MonthIndex,Repo-PRNum,Title,PRAuthor,CodingTimeSecs,PickupTimeSecs,"
        + "ReviewTimeSecs,Review1,Review2,Review3,Review4,Review5,Review6\n");
  }
  
  /**
   * Write a CSV row to the provided writer for this analysed PR. Calls flush on the writer
   * to ensure an incomplete execution still provides output.
   * @param fw
   * @throws IOException
   */
  public void writeToCSV(Writer fw) throws IOException {
    
    if (!isAnalysed) {
      throw new IllegalStateException("PR has not been analysed yet.");
    }
    
    String repoNum = repoName + "/" + prNum;
    String safeCSVTitle = StringEscapeUtils.escapeCsv(prTitle);
    String reviewUserNameStr = prReviewedByList.stream().collect(Collectors.joining(","));

    fw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
        fullFormat.format(mergedAtDate),
        monthOnlyFormat.format(mergedAtDate),
        repoNum,
        safeCSVTitle,
        prAuthorStr,
        codingTimeSecs,
        pickupTimeSecs,
        reviewTimeSecs,
        reviewUserNameStr));
  
    fw.flush();
  }
  
  /**
   * Not all GHUser have a name set so we failover to the login. For use in streams we also
   * need to wrap the possible IOException
   * @param user
   * @return
   */
  private static String getSafeUserNameStr(GHUser user) {
    try {
      if (StringUtils.isEmpty(user.getName())) {
        if (user.getLogin() == null) {
          throw new IllegalArgumentException("Idiot check - Failed to get login id for user");
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
  
  /**
   * Comparator for review created time at doesn't throw IOExceptions
   * @param rev1
   * @param rev2
   * @return
   */
  private static int compareReviewsByCreatedDate(GHPullRequestReview rev1,
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
    } catch (IOException e) {
      throw new IllegalStateException("Failed to extract created date from review "
          + rev2.getNodeId(), e);
    }
    
    return rev1Created.compareTo(rev2Created);
  }
}
