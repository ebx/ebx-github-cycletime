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

package com.echobox.github.cycletime.analyse;

import com.echobox.github.cycletime.data.AnalysedPR;
import com.echobox.github.cycletime.providers.Commit;
import com.echobox.github.cycletime.providers.PRReview;
import com.echobox.github.cycletime.providers.PullRequest;
import com.google.common.collect.Sets;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a PR, analyses it's content and then writes
 * @author MarcF
 */
public class PRAnalyser {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final Set<PRReviewState> STATES_COUNTED_FOR_REVIEW =
      Sets.newHashSet(PRReviewState.CHANGES_REQUESTED, PRReviewState.APPROVED,
          PRReviewState.DISMISSED);
  
  private static final int MIN_SECS_REQUIRED_BETWEEN_REVIEWS = 15 * 60;
  
  private static final int MAX_SECS_CYCLE_COMPONENT = 30 * 24 * 60 * 60; //30 days

  private final PullRequest pullRequest;
  
  @Getter
  private final String repoName;
  @Getter
  private Date mergedAtDate;
  @Getter
  private int prNum;
  @Getter
  private String prTitle;
  @Getter
  private String prAuthorStr;
  @Getter
  private long codingTimeSecs;
  @Getter
  private long pickupTimeSecs;
  @Getter
  private long reviewTimeSecs;
  @Getter
  private List<String> prReviewedByList;
  @Getter
  private volatile boolean isAnalysed = false;
  
  public PRAnalyser(String repoName, PullRequest pullRequest) {
    this.repoName = repoName;
    this.pullRequest = pullRequest;
  }

  /**
   * Analyse the PR
   * @return The analyser
   */
  public PRAnalyser analyse() {

    prNum = pullRequest.getNumber();
    prTitle = pullRequest.getTitle();
    prAuthorStr = pullRequest.getAuthor().getName();
    mergedAtDate = pullRequest.getMergedAt();
  
    List<Commit> commits = getSortedCommits(pullRequest);
    List<PRReview> reviews = getSortedValidAndDeduplicatedReviews(pullRequest);

    //PRs might occasionally be merged without a review
    Date firstReviewAtDate = getFirstReviewDate(reviews).orElse(mergedAtDate);
 
    Date lastCommitBeforeFirstReviewDate = getLastCommitBeforeFirstReview(commits,
        firstReviewAtDate);
  
    Date prCreatedAtDate = pullRequest.getCreatedAt();
    
    //Coding finishes at the later of
    // a) the last commit before the review
    // b) when the PR was created
    long codingFinishTimeMillis =
        Math.max(lastCommitBeforeFirstReviewDate.getTime(), prCreatedAtDate.getTime());
  
    Date firstCommitAtDate = getFirstCommitDate(commits).orElse(prCreatedAtDate);
  
    //A lower limit of zero is to protect against things getting out of sync with force pushes.
    //An upper limit is to protect against single anomalous PRs throwing out future analysis.
    
    codingTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (codingFinishTimeMillis - firstCommitAtDate.getTime()) / 1000L));
    
    pickupTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (firstReviewAtDate.getTime() - codingFinishTimeMillis) / 1000L));
    
    reviewTimeSecs = Math.max(0, Math.min(MAX_SECS_CYCLE_COMPONENT,
        (mergedAtDate.getTime() - firstReviewAtDate.getTime()) / 1000L));
  
    prReviewedByList = reviews.stream().map(r -> r.getReviewedByUser().getName())
        .collect(Collectors.toList());

    isAnalysed = true;
    LOGGER.debug("Analysed " + repoName + "/" + prNum + " - " + prTitle);
    
    return this;
  }
  
  private static Optional<Date> getFirstReviewDate(List<PRReview> reviews) {
    return reviews.stream().map(PRReview::getReviewCreatedAt).findFirst();
  }

  private static Optional<Date> getFirstCommitDate(List<Commit> allCommits) {
    return allCommits.stream().map(Commit::getCommitDate).findFirst();
  }
  
  private static List<Commit> getSortedCommits(PullRequest pullRequest) {
    return pullRequest.getCommits().stream()
            .sorted(Comparator.comparing(Commit::getCommitDate))
            .collect(Collectors.toList());
  }
  
  private static Date getLastCommitBeforeFirstReview(List<Commit> allCommits,
      Date firstReviewDate) {
    return allCommits.stream()
        .map(Commit::getCommitDate)
        .filter(c -> c.getTime() < firstReviewDate.getTime())
        .reduce((a, b) -> b)
        .orElse(firstReviewDate);
  }
  
  /**
   * Reviews require some cleanup to ensure we only consider valid ones, as defined in
   * STATES_COUNTED_FOR_REVIEW. We also ignore any reviews that happened within
   * MIN_SECS_REQUIRED_BETWEEN_REVIEWS of each other as this is caused by a duplicate.
   * @param pullRequest The PR for which to filter reviews
   * @return A suitable list of reviews to analyse
   */
  static List<PRReview> getSortedValidAndDeduplicatedReviews(PullRequest pullRequest) {

    List<PRReview> allSortedReviews = pullRequest.getReviews().stream()
        .filter(i -> STATES_COUNTED_FOR_REVIEW.contains(i.getState()))
        .sorted(Comparator.comparing(PRReview::getReviewCreatedAt))
        .collect(Collectors.toList());
  
    List<PRReview> deDuplicatedReviews = new ArrayList<>();
    long lastReviewMillis = 0;
    //Example PR that has duplicate reviews
    //https://github.com/ebx/ebx-linkedin-sdk/pull/218
    //Aim to remove any reviews that are too close to the previous one.
    for (PRReview review : allSortedReviews) {
      long reviewUnixTimeMillis = review.getReviewCreatedAt().getTime();
      if (reviewUnixTimeMillis > lastReviewMillis + (MIN_SECS_REQUIRED_BETWEEN_REVIEWS * 1000)) {
        deDuplicatedReviews.add(review);
        lastReviewMillis = reviewUnixTimeMillis;
      }
    }
    
    return deDuplicatedReviews;
  }
  
  /**
   * Get the analysis wrapped up in an AnalysedPR
   * @return The analysed PR
   */
  public AnalysedPR getAnalysis() {
    if (!isAnalysed) {
      throw new IllegalStateException("PR has not been analysed yet.");
    }
    
    return new AnalysedPR(repoName, mergedAtDate, prNum, prTitle, prAuthorStr, codingTimeSecs,
        pickupTimeSecs, reviewTimeSecs, prReviewedByList);
  }
}
