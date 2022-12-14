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

import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import com.echobox.github.cycletime.providers.kohsuke.PullRequestKohsuke;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Given a repo analyses all PRs between the defined unix time periods and writes results to CSV.
 * @author MarcF
 */
public class RepoAnalyser {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private final GHRepository ghRepository;
  private final long considerOnlyPRsMergedAfterUnixTime;
  private final long considerOnlyPRsMergedBeforeUnixTime;
  private final PullRequestCSVDAO prPersistDAO;
  
  public RepoAnalyser(GHRepository ghRepository,
      long considerOnlyPRsMergedAfterUnixTime, long considerOnlyPRsMergedBeforeUnixTime,
      PullRequestCSVDAO prPersistDAO) {
    
    this.ghRepository = ghRepository;
    this.considerOnlyPRsMergedAfterUnixTime = considerOnlyPRsMergedAfterUnixTime;
    this.considerOnlyPRsMergedBeforeUnixTime = considerOnlyPRsMergedBeforeUnixTime;
    this.prPersistDAO = prPersistDAO;
  }
  
  public void analyseRepo() {
    
    String repoName = ghRepository.getName();
    LOGGER.info("Processing repo " + repoName);
    
    try {
      
      Set<Integer> prsProcessedThisExecution = new HashSet<>();
      
      // We use Sort.UPDATED so that we include PRs that were created at ANY time before the min
      // merge time but likely merged within this time. If PRs get picked up that were merged
      // before this, but updated for a different reason in the time frame of interest, they will
      // be filtered out in processMergedPRs
  
      PagedIterator<GHPullRequest> prsByUpdatedIterator =
          ghRepository.queryPullRequests().state(GHIssueState.CLOSED)
              .sort(GHPullRequestQueryBuilder.Sort.UPDATED).direction(GHDirection.DESC).list()
              .iterator();

      processPRs(ghRepository, considerOnlyPRsMergedAfterUnixTime,
          considerOnlyPRsMergedBeforeUnixTime, prsProcessedThisExecution, prsByUpdatedIterator,
          pr -> {
            try {
              return pr.getUpdatedAt();
            } catch (IOException ioe) {
              throw new IllegalStateException(
                  "Failed to get updated time of PR " + pr.getNumber());
            }
          });
  
    } catch (Exception e) {
      throw new IllegalStateException("Failed to process repo " + repoName, e);
    }
  }
  
  private void processPRs(GHRepository ghRepository,
      long considerOnlyPRsMergedAfterUnixTime,
      long considerOnlyPRsMergedBeforeUnixTime, Set<Integer> prsProcessedThisExecution,
      PagedIterator<GHPullRequest> prIterator, Function<GHPullRequest, Date> prActionTimeFunc)
      throws IOException {
    
    long minPRActionTimeBeforeStoppingProcessing = considerOnlyPRsMergedAfterUnixTime;
    
    String repoName = ghRepository.getName();
    
    while (prIterator.hasNext()) {
      
      GHPullRequest pr = prIterator.next();
      int prNum = pr.getNumber();

      if (prActionTimeFunc.apply(pr).getTime() / 1000L < minPRActionTimeBeforeStoppingProcessing) {
        LOGGER.debug("Stopping as we are now before the min PR action time.");
        break;
      }
  
      if (prsProcessedThisExecution.contains(prNum)) {
        continue;
      } else if (prPersistDAO.isPRAlreadyPersisted(repoName, prNum)) {
        continue;
      }
      
      if (!pr.isMerged()) {
        LOGGER.debug("Skipping PR #" + pr.getNumber() + " as it's not merged.");
        continue;
      }
      
      long prMergedAtUnixTime = pr.getMergedAt().getTime() / 1000L;
      if (prMergedAtUnixTime >= considerOnlyPRsMergedAfterUnixTime
          && prMergedAtUnixTime < considerOnlyPRsMergedBeforeUnixTime) {
        
        PRAnalyser analyser = new PRAnalyser(repoName, new PullRequestKohsuke(pr));
        analyser.analyse();
        prPersistDAO.writeToCSV(analyser.getAnalysis(), null);
        
        prsProcessedThisExecution.add(pr.getNumber());
      }
    }
    
  }
}
