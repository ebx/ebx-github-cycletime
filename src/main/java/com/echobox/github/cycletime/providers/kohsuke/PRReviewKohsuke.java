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

package com.echobox.github.cycletime.providers.kohsuke;

import com.echobox.github.cycletime.analyse.PRReviewState;
import com.echobox.github.cycletime.providers.PRReview;
import com.echobox.github.cycletime.providers.User;
import org.kohsuke.github.GHPullRequestReview;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * An implementation of a PR review using the Kohsuke library
 * @author MarcF
 */
public class PRReviewKohsuke implements PRReview {
  
  private final GHPullRequestReview review;
  
  public PRReviewKohsuke(GHPullRequestReview review) {
    this.review = review;
  }
  
  @Override
  public PRReviewState getState() {
    
    switch (review.getState()) {
      case PENDING:
        return PRReviewState.PENDING;
      case APPROVED:
        return PRReviewState.APPROVED;
      case CHANGES_REQUESTED:
      case REQUEST_CHANGES:
        return PRReviewState.CHANGES_REQUESTED;
      case COMMENTED:
        return PRReviewState.COMMENTED;
      case DISMISSED:
        return PRReviewState.DISMISSED;
      default:
        throw new IllegalStateException("Unexpected review state " + review.getState());
    }
  }
  
  @Override
  public ZonedDateTime getReviewCreatedAt() {
    try {
      Instant instant = Instant.ofEpochMilli(review.getCreatedAt().getTime());
      return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to extract created date from review "
          + review.getNodeId(), e);
    }
  }
  
  @Override
  public User getReviewedByUser() {
    try {
      return new UserKohsuke(review.getUser());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get review user", e);
    }
  }
}

