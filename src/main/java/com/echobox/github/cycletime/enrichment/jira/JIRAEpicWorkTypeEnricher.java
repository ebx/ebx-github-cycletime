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
import com.chavaillaz.jira.client.java.JavaHttpJiraClient;
import com.chavaillaz.jira.domain.Issue;
import com.echobox.github.cycletime.data.AnalysedPR;
import com.echobox.github.cycletime.enrichment.PREnricher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Uses the ticket number at the start of a PR title and provided JIRA instance to determine the
 * epic work type of the ticket. Expects a specific JIRA project structure to work.
 * @author MarcF
 */
public class JIRAEpicWorkTypeEnricher implements PREnricher {
  
  private static final Logger LOGGER = LogManager.getLogger();
  
  /**
   * The expected issue type name for an epic
   */
  private static final String EPIC_ISSUE_TYPE_NAME = "Epic";
  
  /**
   * The expected structure of the issue key in the PR title;
   */
  private static final Pattern EXPECTED_ISSUE_KEY_PATTERN =
      Pattern.compile("^[A-Z]{2,5}-[0-9]+$");
  
  private JiraClient<Issue> client;
  private IssueClient<Issue> issueClient;
  
  public JIRAEpicWorkTypeEnricher(String jiraURL, String jiraLoginEmail, String jiraLoginAPIToken) {
    client = JavaHttpJiraClient.jiraJavaClient(jiraURL)
        .withAuthentication(jiraLoginEmail, jiraLoginAPIToken);
    issueClient = client.getIssueClient();
  }
  
  @Override
  public List<String> getEnrichmentHeaderNames() {
    return Arrays.asList("JIRAEpicWorkType");
  }
  
  @Override
  public void addEnrichmentsToPR(AnalysedPR analysedPR)
      throws ExecutionException, InterruptedException {
    
    Optional<String> issueKeyFromPRTitle = getIssueKeyFromPRTitle(analysedPR.getPrTitle());
    if (issueKeyFromPRTitle.isEmpty()) {
      throw new IllegalArgumentException("The PR title did not contain an issue key in the expected"
          + " format.");
    }
    
    String workType = getEpicWorkTypeForIssueKey(issueKeyFromPRTitle.get());
    
    analysedPR.appendEnrichments(Arrays.asList(workType));
  }
  
  /**
   * Determine the epic work type of the provided JIRA issue key. Issue parent links will be
   * followed until we arrive at the associated epic.
   * @param issueKey
   * @return The work type of the parent issue epic.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  public String getEpicWorkTypeForIssueKey(String issueKey)
      throws ExecutionException, InterruptedException {
    
    Issue currentIssue = issueClient.getIssue(issueKey).get();
    
    while (!getIssueTypeName(currentIssue).equals(EPIC_ISSUE_TYPE_NAME)) {
      currentIssue = issueClient.getIssue(getIssueParentKey(currentIssue)).get();
    }
    
    return getWorkTypeFieldFromEpicIssue(currentIssue);
  }
  
  public static Optional<String> getIssueKeyFromPRTitle(String prTitle) {
    String issueKeyFromPRTitle = prTitle.split(" ")[0];
    if (!EXPECTED_ISSUE_KEY_PATTERN.matcher(issueKeyFromPRTitle).matches()) {
      return Optional.empty();
    }
    return Optional.of(issueKeyFromPRTitle);
  }
  
  private static String getIssueParentKey(Issue issue) {
    if (issue.getFields().getParent() != null) {
      return issue.getFields().getParent().getKey();
    } else {
      return null;
    }
  }
  
  private static String getIssueTypeName(Issue issue) {
    return issue.getFields().getIssueType().getName();
  }

  private static String getWorkTypeFieldFromEpicIssue(Issue epicIssue) {
    if (!getIssueTypeName(epicIssue).equals(EPIC_ISSUE_TYPE_NAME)) {
      throw new IllegalArgumentException("The provided issue is not an EPIC.");
    }
    
    Map<String, Object> customFields = epicIssue.getFields().getCustomFields();
    LinkedHashMap customfield12732 = (LinkedHashMap) customFields.get("customfield_12732");
    String workType = (String) customfield12732.get("value");
    return workType;
  }
}
