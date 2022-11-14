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

import com.echobox.github.cycletime.providers.Commit;
import com.echobox.github.cycletime.providers.PRReview;
import com.echobox.github.cycletime.providers.PullRequest;
import com.echobox.github.cycletime.providers.User;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A PullRequest implementation using the Kohsuke library
 * @author MarcF
 */
public class PullRequestKohsuke implements PullRequest {
  
  private final GHPullRequest ghPullRequest;
  
  public PullRequestKohsuke(GHPullRequest ghPullRequest) {
    this.ghPullRequest = ghPullRequest;
  }
  
  @Override
  public int getNumber() {
    return ghPullRequest.getNumber();
  }
  
  @Override
  public String getTitle() {
    return ghPullRequest.getTitle();
  }
  
  @Override
  public User getAuthor() {
    try {
      return new UserKohsuke(ghPullRequest.getUser());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get PR user", e);
    }
  }
  
  @Override
  public ZonedDateTime getCreatedAt() {
    try {
      Instant instant = Instant.ofEpochMilli(ghPullRequest.getCreatedAt().getTime());
      return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get createdAt time for PR "
          + ghPullRequest.getNumber(), e);
    }
  }
  
  @Override
  public ZonedDateTime getMergedAt() {
    Instant instant = Instant.ofEpochMilli(ghPullRequest.getMergedAt().getTime());
    return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
  
  @Override
  public List<Commit> getCommits() {
    try {
      return ghPullRequest.listCommits().toList().stream()
          .map(c -> new CommitKohsuke(c.getCommit()))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get commits for PR "
          + ghPullRequest.getNumber(), e);
    }
  }
  
  @Override
  public List<PRReview> getReviews() {
    try {
      return ghPullRequest.listReviews().toList().stream()
          .map(PRReviewKohsuke::new)
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get reviews for PR "
          + ghPullRequest.getNumber(), e);
    }
  }
}

