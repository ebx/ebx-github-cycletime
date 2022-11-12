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

import com.echobox.github.cycletime.persist.CSVPersist;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

/**
 * Given a github organisation process all available repositories
 * @author MarcF
 */
public class OrgAnalyser {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  private final GHOrganization githubOrg;
  private final long considerOnlyPRsMergedAfterUnixTime;
  private final long considerOnlyPRsMergedBeforeUnixTime;
  private final CSVPersist csv;
  
  public OrgAnalyser(GHOrganization githubOrg, long considerOnlyPRsMergedAfterUnixTime,
      long considerOnlyPRsMergedBeforeUnixTime, CSVPersist csv) {
  
    this.githubOrg = githubOrg;
    this.considerOnlyPRsMergedAfterUnixTime = considerOnlyPRsMergedAfterUnixTime;
    this.considerOnlyPRsMergedBeforeUnixTime = considerOnlyPRsMergedBeforeUnixTime;
    this.csv = csv;
  }

  public void analyseOrg() {
    PagedIterable<GHRepository> ghRepositories = githubOrg.listRepositories();
    ghRepositories.forEach(ghRepository -> {
      RepoAnalyser repoAnalyser = new RepoAnalyser(ghRepository, considerOnlyPRsMergedAfterUnixTime,
          considerOnlyPRsMergedBeforeUnixTime, csv);
      repoAnalyser.analyseRepo();
    });
  }
}
