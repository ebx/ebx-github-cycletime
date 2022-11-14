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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A CSV DAO for preferred author names
 * @author MarcF
 */
public class PreferredAuthorNamesCSVDAO implements AutoCloseable {
  
  private static final Logger LOGGER = LogManager.getLogger();

  private final Reader csvReader;
  
  public PreferredAuthorNamesCSVDAO(String filename) throws IOException {
    this.csvReader = new FileReader(filename);
  }
  
  /**
   * Load all preferred names from the configured CSV
   * @return Map of KEY - NameToMatch, VALUE - PreferredName
   * @throws IOException If the configured CSV file cannot be read
   */
  public synchronized Map<String, String> loadAllPreferredNames() throws IOException {

    CSVFormat format = CSVFormat.Builder.create().setHeader().build();
    Iterable<CSVRecord> records = format.parse(csvReader);
  
    return StreamSupport.stream(records.spliterator(), false)
        .filter(r -> !StringUtils.isEmpty(r.get(0)))
        .collect(Collectors.toMap(r -> r.get("NameToMatch"), r -> r.get("PreferredName")));
  }
  
  @Override
  public void close() throws Exception {
    csvReader.close();
  }
}
