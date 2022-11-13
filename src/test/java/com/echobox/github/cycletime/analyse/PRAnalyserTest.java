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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.echobox.github.cycletime.providers.Commit;
import com.echobox.github.cycletime.providers.CommitTestImpl;
import com.echobox.github.cycletime.providers.PRReview;
import com.echobox.github.cycletime.providers.PRReviewTestImpl;
import com.echobox.github.cycletime.providers.PullRequest;
import com.echobox.github.cycletime.providers.UserTestImpl;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests of the PRAnalyser
 * @author MarcF
 */
@ExtendWith(MockitoExtension.class)
public class PRAnalyserTest {
  
  @Mock
  private PullRequest pr;
  
  @Test
  public void testAnalyseWithPRCreatedBEFORELastCommit() {
    
    List<Commit> commits = Lists.newArrayList(
        new CommitTestImpl(getDate(1 * 60)),
        new CommitTestImpl(getDate(5 * 60)),
        new CommitTestImpl(getDate(10 * 60)));
    when(pr.getCommits()).thenReturn(commits);
    
    List<PRReview> reviews = Lists.newArrayList(
        new PRReviewTestImpl(PRReviewState.CHANGES_REQUESTED, getDate(20 * 60), "User2"),
        new PRReviewTestImpl(PRReviewState.APPROVED, getDate(60 * 60), "User2"));
    when(pr.getReviews()).thenReturn(reviews);
    
    when(pr.getCreatedAt()).thenReturn(getDate(8 * 60));
    when(pr.getMergedAt()).thenReturn(getDate(80 * 60));
    when(pr.getAuthor()).thenReturn(new UserTestImpl("User1"));
    
    PRAnalyser analyser = new PRAnalyser("test", pr);
    assertFalse(analyser.isAnalysed());
    analyser.analyse();
    assertTrue(analyser.isAnalysed());
  
    assertEquals("User1", analyser.getPrAuthorStr());
    
    assertEquals(9 * 60, analyser.getCodingTimeSecs());
    assertEquals(10 * 60, analyser.getPickupTimeSecs());
    assertEquals(60 * 60, analyser.getReviewTimeSecs());
    
    String prReviewedBy = analyser.getPrReviewedByList().stream().collect(Collectors.joining(","));
    assertEquals("User2,User2", prReviewedBy);
  }

  @Test
  public void testAnalyseWithPRCreatedAFTERLastCommitWithCommitsReversed() {
    List<Commit> commits = Lists.newArrayList(
        //commits deliberately reversed to ensure they get sorted before analysis
        new CommitTestImpl(getDate(10 * 60)),
        new CommitTestImpl(getDate(5 * 60)),
        new CommitTestImpl(getDate(1 * 60)));
    when(pr.getCommits()).thenReturn(commits);
    
    List<PRReview> reviews = new ArrayList<>();
    reviews.add(new PRReviewTestImpl(PRReviewState.CHANGES_REQUESTED, getDate(20 * 60), "User2"));
    reviews.add(new PRReviewTestImpl(PRReviewState.APPROVED, getDate(60 * 60), "User2"));
    when(pr.getReviews()).thenReturn(reviews);
    
    when(pr.getCreatedAt()).thenReturn(getDate(12 * 60));
    when(pr.getMergedAt()).thenReturn(getDate(80 * 60));
    when(pr.getAuthor()).thenReturn(new UserTestImpl("User1"));
    
    PRAnalyser analyser = new PRAnalyser("test", pr).analyse();
    
    assertEquals("User1", analyser.getPrAuthorStr());
    
    assertEquals(11 * 60, analyser.getCodingTimeSecs());
    assertEquals(8 * 60, analyser.getPickupTimeSecs());
    assertEquals(60 * 60, analyser.getReviewTimeSecs());
    
    String prReviewedBy = analyser.getPrReviewedByList().stream().collect(Collectors.joining(","));
    assertEquals("User2,User2", prReviewedBy);
  }
  
  @Test
  public void testAnalyseWithoutAnyReviews() {
    List<Commit> commits = Lists.newArrayList(
        new CommitTestImpl(getDate(1 * 60)),
        new CommitTestImpl(getDate(5 * 60)),
        new CommitTestImpl(getDate(10 * 60)));
    when(pr.getCommits()).thenReturn(commits);
  
    List<PRReview> reviews = new ArrayList<>();
    when(pr.getReviews()).thenReturn(reviews);
  
    when(pr.getCreatedAt()).thenReturn(getDate(8 * 60));
    when(pr.getMergedAt()).thenReturn(getDate(80 * 60));
    when(pr.getAuthor()).thenReturn(new UserTestImpl("User1"));
  
    PRAnalyser analyser = new PRAnalyser("test", pr).analyse();

    assertEquals(9 * 60, analyser.getCodingTimeSecs());
    assertEquals(70 * 60, analyser.getPickupTimeSecs());
    assertEquals(0 * 60, analyser.getReviewTimeSecs());
  
    String prReviewedBy = analyser.getPrReviewedByList().stream().collect(Collectors.joining(","));
    assertEquals("", prReviewedBy);
  }
  
  @Test
  public void testAnalyseWithNoCommitsBeforeReview() {
    List<Commit> commits = new ArrayList<>();
    when(pr.getCommits()).thenReturn(commits);
  
    List<PRReview> reviews = new ArrayList<>();
    reviews.add(new PRReviewTestImpl(PRReviewState.CHANGES_REQUESTED, getDate(20 * 60), "User2"));
    reviews.add(new PRReviewTestImpl(PRReviewState.APPROVED, getDate(60 * 60), "User2"));
    when(pr.getReviews()).thenReturn(reviews);
  
    when(pr.getCreatedAt()).thenReturn(getDate(8 * 60));
    when(pr.getMergedAt()).thenReturn(getDate(80 * 60));
    when(pr.getAuthor()).thenReturn(new UserTestImpl("User1"));
  
    PRAnalyser analyser = new PRAnalyser("test", pr).analyse();
  
    assertEquals(12 * 60, analyser.getCodingTimeSecs());
    assertEquals(0 * 60, analyser.getPickupTimeSecs());
    assertEquals(60 * 60, analyser.getReviewTimeSecs());
  }

  @Test
  public void testGetValidAndDeduplicatedReviews() {
    
    List<PRReview> prs = Lists.newArrayList(
        //Should be removed as not a valid review type
        new PRReviewTestImpl(PRReviewState.COMMENTED, getDate(10), "User1"),
        //Should be removed as too close to other APPROVED review
        //This is also deliberately out of order to ensure the sort is tested
        new PRReviewTestImpl(PRReviewState.APPROVED, getDate(34 * 60), "User2"),
        new PRReviewTestImpl(PRReviewState.APPROVED, getDate(20 * 60), "User2"));
    
    when(pr.getReviews()).thenReturn(prs);
    
    List<PRReview> filteredReviews = PRAnalyser.getSortedValidAndDeduplicatedReviews(pr);
    
    assertEquals(1, filteredReviews.size());
    assertEquals(20 * 60, filteredReviews.get(0).getReviewCreatedAt().getTime() / 1000L);
  }
  
  /**
   * Helper to build dates easily
   * @param epochSeconds The seconds since epoch
   * @return A corresponding date
   */
  private static Date getDate(long epochSeconds) {
    return Date.from(Instant.ofEpochSecond(epochSeconds));
  }
}
