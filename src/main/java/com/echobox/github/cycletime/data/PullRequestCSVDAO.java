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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A CSV DAO for PR analysis results
 * @author MarcF
 */
public class PullRequestCSVDAO implements AutoCloseable {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS z";
  private static final DateTimeFormatter fullFormat = DateTimeFormatter.ofPattern(ISO_FORMAT);
  private static final DateTimeFormatter monthOnlyFormat = DateTimeFormatter.ofPattern("MM");

  private final Writer csvWriter;
  private final Reader csvReader;
  private final ZoneId persistWithTimezone;
  
  public PullRequestCSVDAO(String filename, ZoneId persistWithTimezone, boolean append)
      throws IOException {
    
    this.csvWriter = new PrintWriter(new FileWriter(filename, append));
    this.csvReader = new FileReader(filename);
    this.persistWithTimezone = persistWithTimezone;

  }

  /**
   * Write the CSV header for PR analysis
   * @throws IOException If the write fails
   */
  public synchronized void writeCSVHeader() throws IOException {
    csvWriter.write("UTCMergedDateTime,MonthIndex,Repo-PRNum,Title,PRAuthor,CodingTimeSecs,"
        + "PickupTimeSecs,ReviewTimeSecs,Review1,Review2,Review3,Review4,Review5,Review6\n");
  }
  
  public synchronized  void writeToCSV(List<AnalysedPR> analysedPRs,
      Map<String, String> preferredAuthorNames) throws IOException {
    for (AnalysedPR analysedPR : analysedPRs) {
      writeToCSV(analysedPR, preferredAuthorNames);
    }
  }
  
  /**
   * Write a CSV row to the provided writer for this analysed PR. Calls flush on the writer
   * to ensure an incomplete execution still provides output.
   * @param  analysedPR The analysed PR
   * @param  preferredAuthorNames Preferred author names
   * @throws IOException If the write fails
   */
  public synchronized void writeToCSV(AnalysedPR analysedPR,
      Map<String, String> preferredAuthorNames) throws IOException {
    
    String repoNum = analysedPR.getRepoName() + "/" + analysedPR.getPrNum();
    String safeCSVTitle = StringEscapeUtils.escapeCsv(analysedPR.getPrTitle());
    String authorName = analysedPR.getPrAuthorStr();
  
    Stream<String> reviewedByStream = analysedPR.getPrReviewedByList().stream();
    
    if (preferredAuthorNames != null) {
      authorName = preferredAuthorNames.getOrDefault(authorName, authorName);
      reviewedByStream = reviewedByStream.map(n -> preferredAuthorNames.getOrDefault(n, n));
    }
  
    String reviewUserNameStr = reviewedByStream.collect(Collectors.joining(","));
    
    ZonedDateTime dtToPrint = analysedPR.getMergedAtDate().withZoneSameInstant(persistWithTimezone);
    
    csvWriter.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
        fullFormat.format(dtToPrint),
        monthOnlyFormat.format(dtToPrint),
        repoNum,
        safeCSVTitle,
        authorName,
        analysedPR.getCodingTimeSecs(),
        analysedPR.getPickupTimeSecs(),
        analysedPR.getReviewTimeSecs(),
        reviewUserNameStr));
  
    csvWriter.flush();
  }
  
  public synchronized List<AnalysedPR> loadAllData() throws IOException {

    CSVFormat format = CSVFormat.Builder.create().setHeader().build();
    Iterable<CSVRecord> records = format.parse(csvReader);
  
    return StreamSupport.stream(records.spliterator(), false)
        .map(r -> parseRecord(r))
        .collect(Collectors.toList());
  }
  
  private AnalysedPR parseRecord(CSVRecord record) {
    
    String repoPRNum = record.get("Repo-PRNum");
    
    String mergedAtDateString = record.get("UTCMergedDateTime");
    ZonedDateTime mergedAtDate = ZonedDateTime.parse(mergedAtDateString, fullFormat);
  
    List<String> prReviewsBy = new ArrayList<>();
    getReviewer(prReviewsBy, record, "Review1");
    getReviewer(prReviewsBy, record, "Review2");
    getReviewer(prReviewsBy, record, "Review3");
    getReviewer(prReviewsBy, record, "Review4");
    getReviewer(prReviewsBy, record, "Review5");
    getReviewer(prReviewsBy, record, "Review6");
  
    return new AnalysedPR(repoPRNum.split("/")[0],
        mergedAtDate,
        Integer.parseInt(repoPRNum.split("/")[1]),
        record.get("Title"),
        record.get("PRAuthor"),
        Long.parseLong(record.get("CodingTimeSecs")),
        Long.parseLong(record.get("PickupTimeSecs")),
        Long.parseLong(record.get("ReviewTimeSecs")),
        prReviewsBy
    );
  }
  
  private void getReviewer(List<String> prReviewsBy, CSVRecord record, String column) {
    if (record.isSet(column) && !StringUtils.isEmpty(record.get(column))) {
      prReviewsBy.add(record.get(column));
    }
  }
  
  @Override
  public void close() throws Exception {
    csvWriter.close();
    csvReader.close();
  }
}
