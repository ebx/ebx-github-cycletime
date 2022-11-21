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
import com.echobox.github.cycletime.data.PreferredAuthorNamesCSVDAO;
import com.echobox.github.cycletime.data.PullRequestCSVDAO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility that takes a raw export of AnalysedPRs and applies some desirable post processing
 * @author MarcF
 */
public class AnalysisCleanup {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private final List<AnalysedPR> uncleanedPRs;
  private final PreferredAuthorNamesCSVDAO preferredAuthorsNamesDAO;
  private final PullRequestCSVDAO cleanedDAOOut;
  private final PullRequestCSVDAO cleanedDAOExcludedAuthorOut;
  private final List<String> authorsToFilterOut;
  
  /**
   * Create a cleanup instance
   * @param uncleanedPRs The raw uncleaned PRs, directly from the repo
   * @param preferredAuthorsNamesDAO A DAO for loading preferred author name mappings
   * @param cleanedDAOOut A DAO for the cleaned PRs
   * @param cleanedDAOExcludedAuthorOut A DAO for the cleaned PRs that contains any excluded
   * @param authorsToFilterOut Authors to filter into a dedicated output CSV
   * authors.
   */
  public AnalysisCleanup(List<AnalysedPR> uncleanedPRs,
      PreferredAuthorNamesCSVDAO preferredAuthorsNamesDAO,
      PullRequestCSVDAO cleanedDAOOut, PullRequestCSVDAO cleanedDAOExcludedAuthorOut,
      List<String> authorsToFilterOut) {
    this.uncleanedPRs = uncleanedPRs;
    this.preferredAuthorsNamesDAO = preferredAuthorsNamesDAO;
    this.cleanedDAOOut = cleanedDAOOut;
    this.cleanedDAOExcludedAuthorOut = cleanedDAOExcludedAuthorOut;
    this.authorsToFilterOut = authorsToFilterOut;
  }

  public void clean() throws IOException {
    Map<String, String> preferredAuthorNames = preferredAuthorsNamesDAO.loadAllPreferredNames();
  
    List<AnalysedPR> sortedPRs = uncleanedPRs.stream()
        .filter(pr -> !matchAuthorFilterList(pr))
        .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
        .collect(Collectors.toList());
  
    cleanedDAOOut.writeToCSV(sortedPRs, preferredAuthorNames);
  
    List<AnalysedPR> sortedPRsExcludedAuthors = uncleanedPRs.stream()
        .filter(pr -> matchAuthorFilterList(pr))
        .sorted(Comparator.comparing(pr -> pr.getMergedAtDate()))
        .collect(Collectors.toList());
  
    cleanedDAOExcludedAuthorOut.writeToCSV(sortedPRsExcludedAuthors, preferredAuthorNames);
  
    LOGGER.debug("Completed analysis cleanup.");
  }

  private boolean matchAuthorFilterList(AnalysedPR pr) {
    for (String regex : authorsToFilterOut) {
      if (pr.getPrAuthorStr().matches(regex)) {
        return true;
      }
    }
    return false;
  }
}
