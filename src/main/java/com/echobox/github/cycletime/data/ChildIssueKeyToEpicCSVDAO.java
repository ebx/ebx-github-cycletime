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
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A CSV DAO for issue key to epic information
 * @author MarcF
 */
public class ChildIssueKeyToEpicCSVDAO implements AutoCloseable {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private final Writer csvWriter;
  private final Reader csvReader;
  
  public ChildIssueKeyToEpicCSVDAO(String filename, boolean append) throws IOException {
    this.csvWriter = new PrintWriter(new FileWriter(filename, append));
    this.csvReader = new FileReader(filename);
  }
  
  public synchronized void writeCSVHeader() throws IOException {
    String headers = "ChildIssueKey,EpicIssueKey,WorkType,Title";
    csvWriter.write(headers + "\n");
    csvWriter.flush();
  }
  
  public synchronized void writeToCSV(List<Epic> epics) throws IOException  {
    for (Epic epic : epics) {
      writeToCSV(epic);
    }
  }
  
  public synchronized void writeToCSV(Epic epic) throws IOException  {
    String safeCSVTitle = StringEscapeUtils.escapeCsv(epic.getTitle());
    
    String csvLineToWrite = String.format("%s,%s,%s,%s",
        epic.getChildIssueKey(),
        epic.getEpicIssueKey(),
        epic.getWorkType(),
        safeCSVTitle);
    
    csvWriter.write(csvLineToWrite + "\n");
    csvWriter.flush();
  }
  
  /**
   * Load all child issue keys to epics
   * @return Map of KEY - Child issue key, VALUE - The associated epic
   * @throws IOException If the configured CSV file cannot be read
   */
  public synchronized Map<String, Epic> loadAllEpics() throws IOException {

    if (csvReader == null) {
      return new HashMap<>();
    }

    CSVFormat format = CSVFormat.Builder.create().setHeader().build();
    Iterable<CSVRecord> records = format.parse(csvReader);

    return StreamSupport.stream(records.spliterator(), false)
        .map(r -> parseRecord(r))
        .collect(Collectors.toMap(r -> r.getChildIssueKey(), r -> r));
  }
  
  private Epic parseRecord(CSVRecord record) {
    
    String childIssueKey = record.get("ChildIssueKey");
    String epicIssueKey = record.get("EpicIssueKey");
    String title  = record.get("Title");
    String workType = record.get("WorkType");
    
    return new Epic(childIssueKey, epicIssueKey, title, workType);
  }
  
  @Override
  public void close() throws Exception {
    if (csvReader != null) {
      csvReader.close();
    }
  }
}
