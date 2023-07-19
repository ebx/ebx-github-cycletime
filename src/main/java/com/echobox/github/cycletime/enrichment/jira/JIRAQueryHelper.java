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

package com.echobox.github.cycletime.enrichment.jira;

import com.chavaillaz.jira.client.IssueClient;
import com.chavaillaz.jira.client.JiraClient;
import com.chavaillaz.jira.client.apache.ApacheHttpJiraClient;
import com.chavaillaz.jira.domain.Issue;
import com.chavaillaz.jira.domain.Issues;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A class for making JIRA queries
 * @author MarcF
 */
public class JIRAQueryHelper {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  /**
   * The maximum results we can get from the API in one request
   */
  private static final int MAX_RESULTS_PER_QUERY = 100;
  
  private JiraClient<Issue> client;
  private IssueClient<Issue> issueClient;
  
  public JIRAQueryHelper(String jiraURL, String jiraLoginEmail, String jiraLoginAPIToken) {
    client = ApacheHttpJiraClient.jiraApacheClient(jiraURL)
        .withAuthentication(jiraLoginEmail, jiraLoginAPIToken);
    issueClient = client.getIssueClient();
  }
  
  public List<Issue> executeQuery(String query) throws ExecutionException, InterruptedException {
    
    List<Issue> results = new ArrayList<>();
    int lastResultsNum = MAX_RESULTS_PER_QUERY;
    
    do {
      Issues<Issue> issues =
          client.getSearchClient().searchIssues(query, results.size(), MAX_RESULTS_PER_QUERY).get();
      results.addAll(issues);
      lastResultsNum = issues.size();
      
    } while (lastResultsNum == MAX_RESULTS_PER_QUERY);

    return results;
  }
}
