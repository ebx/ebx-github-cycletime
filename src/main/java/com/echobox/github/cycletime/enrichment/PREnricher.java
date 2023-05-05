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

package com.echobox.github.cycletime.enrichment;

import com.echobox.github.cycletime.data.AnalysedPR;

import java.util.List;

/**
 * Provides enrichment for an AnalysedPR. For example data from JIRA.
 * @author MarcF
 */
public interface PREnricher {
  
  /**
   * Provide the enrichment headers names provided by this implementation
   * @return The enrichment header names
   */
  List<String> getEnrichmentHeaderNames();
  
  /**
   * Given an analysed PR add the expected enrichments
   * @param analysedPR The PR for which to add the enrichments
   */
  void addEnrichmentsToPR(AnalysedPR analysedPR) throws Exception;

}
