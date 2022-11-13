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

package com.echobox.github.cycletime.persist;

import com.echobox.github.cycletime.analyse.PRAnalyser;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Persist analysis to CSV files
 * @author MarcF
 */
public class CSVPersist {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
  private static final SimpleDateFormat fullFormat = new SimpleDateFormat(ISO_FORMAT);
  private static final SimpleDateFormat monthOnlyFormat = new SimpleDateFormat("MM");
  
  private final Writer csvWriter;
  
  public CSVPersist(Writer csvWriter, TimeZone persistWithTimezone) {
    this.csvWriter = csvWriter;

    fullFormat.setTimeZone(persistWithTimezone);
    monthOnlyFormat.setTimeZone(persistWithTimezone);
  }

  /**
   * Write the CSV header for PR analysis
   * @throws IOException If the write fails
   */
  public synchronized void writeCSVHeader() throws IOException {
    csvWriter.write("UTCMergedDateTime,MonthIndex,Repo-PRNum,Title,PRAuthor,CodingTimeSecs,"
        + "PickupTimeSecs,ReviewTimeSecs,Review1,Review2,Review3,Review4,Review5,Review6\n");
  }
  
  /**
   * Write a CSV row to the provided writer for this analysed PR. Calls flush on the writer
   * to ensure an incomplete execution still provides output.
   * @param  analyser The PRAnalyser
   * @throws IOException If the write fails
   */
  public synchronized void writeToCSV(PRAnalyser analyser) throws IOException {
    
    if (!analyser.isAnalysed()) {
      throw new IllegalStateException("PR has not been analysed yet.");
    }
    
    String repoNum = analyser.getRepoName() + "/" + analyser.getPrNum();
    String safeCSVTitle = StringEscapeUtils.escapeCsv(analyser.getPrTitle());
    String reviewUserNameStr = analyser.getPrReviewedByList()
        .stream().collect(Collectors.joining(","));
  
    csvWriter.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
        fullFormat.format(analyser.getMergedAtDate()),
        monthOnlyFormat.format(analyser.getMergedAtDate()),
        repoNum,
        safeCSVTitle,
        analyser.getPrAuthorStr(),
        analyser.getCodingTimeSecs(),
        analyser.getPickupTimeSecs(),
        analyser.getReviewTimeSecs(),
        reviewUserNameStr));
  
    csvWriter.flush();
  }
}
