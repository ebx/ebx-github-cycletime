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
import com.echobox.github.cycletime.data.AuthorsToSquadsCSVDAO;
import com.echobox.github.cycletime.data.CycleTimeGroup;
import com.google.common.collect.Lists;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calculates daily squad cycle times from PRs
 * @author MarcF
 */
public class SquadDailyCycleTimeCalculator {

  private static final Logger LOGGER = LogManager.getLogger();
  
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String PERSIST_HOURS_FORMAT = "%.2f";
  
  private final List<AnalysedPR> prs;
  private final AuthorsToSquadsCSVDAO authorsToSquadDAO;
  private final ZoneId persistWithTimezone;
  private final String resultCSVPostfix;
  
  private Map<String, List<CycleTimeGroup>> cycleTimesByGroup = new HashMap<>();

  public SquadDailyCycleTimeCalculator(List<AnalysedPR> prs,
      AuthorsToSquadsCSVDAO authorsToSquadDAO, ZoneId persistWithTimezone,
      String resultCSVPostfix) {
    
    this.prs = prs;
    this.authorsToSquadDAO = authorsToSquadDAO;
    this.persistWithTimezone = persistWithTimezone;
    this.resultCSVPostfix = resultCSVPostfix;
  }

  public void calculate() throws IOException {
    
    Map<String, List<String>> authorsToSquad = authorsToSquadDAO.loadAllAuthorsToSquad();

    ZonedDateTime firstDateTime = prs.stream().map(AnalysedPR::getMergedAtDate)
        .min(ZonedDateTime::compareTo).get().withZoneSameInstant(persistWithTimezone);
    ZonedDateTime lastDateTime = prs.stream().map(AnalysedPR::getMergedAtDate)
        .max(ZonedDateTime::compareTo).get().withZoneSameInstant(persistWithTimezone);
  
    ZonedDateTime startAtMidnight = firstDateTime.withHour(0).withMinute(0).withSecond(0);
    ZonedDateTime midnightAtEnd = lastDateTime.withHour(0).withMinute(0).withSecond(0).plusDays(1);
  
    setupCycleTimeGroups(authorsToSquad, startAtMidnight, midnightAtEnd);
  
    Map<String, AtomicInteger> numPRsBucketedByAuthor = new HashMap<>();
  
    for (AnalysedPR pr : prs) {

      ZonedDateTime mergedDateTime = pr.getMergedAtDate().withZoneSameInstant(persistWithTimezone);
      long daysDiff = ChronoUnit.DAYS.between(startAtMidnight, mergedDateTime);
    
      String author = pr.getPrAuthorStr();
      List<String> authorInSquads = authorsToSquad.get(author);
      numPRsBucketedByAuthor.computeIfAbsent(author, k -> new AtomicInteger(0));
      if (authorInSquads == null || authorInSquads.isEmpty()) {
        authorInSquads = Lists.newArrayList("None");
      } else {
        numPRsBucketedByAuthor.get(author).incrementAndGet();
      }
    
      //Authors can be in more than one squad
      for (String squad : authorInSquads) {
        CycleTimeGroup group = cycleTimesByGroup.get(squad).get((int) daysDiff);
        if (group.getStartDateTime().isBefore(pr.getMergedAtDate())
            && group.getEndDateTime().isAfter(pr.getMergedAtDate())) {
          group.addValues(pr.getCodingTimeSecs(), pr.getPickupTimeSecs(), pr.getReviewTimeSecs());
        } else {
          throw new IllegalStateException("Idiot check - Unexpected bucket selected.");
        }
      }
    }
  
    logWarningForAnyAuthorsNotBucketed(numPRsBucketedByAuthor);
  }

  private void setupCycleTimeGroups(Map<String, List<String>> authorsToSquad,
      ZonedDateTime startAtMidnight, ZonedDateTime midnightAtEnd) {
    
    List<String> distinctSquads = Stream.concat(
        authorsToSquad.entrySet().stream().flatMap(r -> r.getValue().stream()),
        Stream.of("None"))
        .distinct().collect(Collectors.toList());
    
    for (String squad : distinctSquads) {
      List<CycleTimeGroup> buckets = new ArrayList<>();
      ZonedDateTime currentDateTime = startAtMidnight;
      while (currentDateTime.isBefore(midnightAtEnd)) {
        ZonedDateTime currentPlusOneDay = currentDateTime.plusDays(1);
        buckets.add(new CycleTimeGroup(currentDateTime, currentPlusOneDay));
        currentDateTime = currentPlusOneDay;
      }
      cycleTimesByGroup.put(squad, buckets);
    }
  }
  
  private void logWarningForAnyAuthorsNotBucketed(
      Map<String, AtomicInteger> numPRsBucketedByAuthor) {
    
    //This is a validation of the authorsToSquad map
    List<String> authorsNeverSeenInSquad = numPRsBucketedByAuthor.entrySet()
        .stream().filter(e -> e.getValue().get() == 0)
        .map(e -> e.getKey()).distinct().collect(Collectors.toList());
    
    for (String author : authorsNeverSeenInSquad) {
      LOGGER.warn("Never detected author '" + author + "' in squad, added them to squad"
          + " 'NONE'.");
    }
  }
  
  public void persistToCSV() throws IOException {

    List<String> headers = Lists.newArrayList("", "CodingTimeHours", "PickupTimeHours",
        "ReviewTimeHours", "TotalTimeHours");
    CSVFormat csvFormat = CSVFormat.Builder.create()
        .setHeader(headers.toArray(new String[0])).build();

    for (String groupListKey : cycleTimesByGroup.keySet()) {
      FileWriter fw = new FileWriter(groupListKey + resultCSVPostfix);
    
      try (CSVPrinter printer = new CSVPrinter(fw, csvFormat)) {
      
        List<CycleTimeGroup> groupList = cycleTimesByGroup.get(groupListKey);
      
        for (CycleTimeGroup group : groupList) {
        
          String dateStr = DATE_TIME_FORMATTER.format(group.getStartDateTime());
          
          String codingTimeStr = "";
          
          double totalTimeHours = 0;
          
          if (!group.getCodingTimeSecsValues().isEmpty()) {
            double averageInHours = group.getAverageCodingTimeSecs().getAsDouble() / 3600;
            totalTimeHours += averageInHours;
            codingTimeStr =  String.format(PERSIST_HOURS_FORMAT, averageInHours);
          }
        
          String pickupTimeStr = "";
          if (!group.getPickupTimeSecsValues().isEmpty()) {
            double averageInHours = group.getAveragePickupTimeSecs().getAsDouble() / 3600;
            totalTimeHours += averageInHours;
            pickupTimeStr = String.format(PERSIST_HOURS_FORMAT, averageInHours);
          }
        
          String reviewTimeStr = "";
          if (!group.getReviewTimeSecsValues().isEmpty()) {
            double averageInHours = group.getAverageReviewTimeSecs().getAsDouble() / 3600;
            totalTimeHours += averageInHours;
            reviewTimeStr = String.format(PERSIST_HOURS_FORMAT, averageInHours);
          }
        
          String totalTimeStr = "";
          if (totalTimeHours > 0) {
            totalTimeStr = String.format(PERSIST_HOURS_FORMAT, totalTimeHours);
          }
          
          printer.printRecord(dateStr, codingTimeStr, pickupTimeStr, reviewTimeStr, totalTimeStr);
        }
      }
    }

  }
}
