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

package com.echobox.github.cycletime.data;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
public class AnalysedPR {
  
  @NonNull
  private String repoName;
  @NonNull
  private ZonedDateTime mergedAtDate;
  @NonNull
  private int prNum;
  @NonNull
  private String prTitle;
  @NonNull
  private String prAuthorStr;
  @NonNull
  private long codingTimeSecs;
  @NonNull
  private long pickupTimeSecs;
  @NonNull
  private long reviewTimeSecs;
  @NonNull
  private List<String> prReviewedByList;
  
  private List<String> additionalEnrichments;

  public synchronized void appendEnrichments(List<String> enrichments) {
    if (additionalEnrichments == null) {
      additionalEnrichments = new ArrayList<>();
    }
    
    additionalEnrichments.addAll(enrichments);
  }
  
}
