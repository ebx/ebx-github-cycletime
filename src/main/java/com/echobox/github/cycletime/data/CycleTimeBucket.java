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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

@Getter
public class CycleTimeBucket {
  
  private final ZonedDateTime startDateTime;
  private final ZonedDateTime endDateTime;
  
  @Getter
  private final List<Long> codingTimeSecsValues = new ArrayList<>();
  
  @Getter
  private final List<Long> pickupTimeSecsValues = new ArrayList<>();
  
  @Getter
  private final List<Long> reviewTimeSecsValues = new ArrayList<>();
  
  public CycleTimeBucket(ZonedDateTime startDateTime, ZonedDateTime endDateTime) {
    this.startDateTime = startDateTime;
    this.endDateTime = endDateTime;
  }
  
  public void addValues(long codingTimeSecs, long pickupTimeSecs, long reviewTimeSecs) {
    codingTimeSecsValues.add(codingTimeSecs);
    pickupTimeSecsValues.add(pickupTimeSecs);
    reviewTimeSecsValues.add(reviewTimeSecs);
  }
  
  public OptionalDouble getAverageCodingTimeSecs() {
    return codingTimeSecsValues.stream().mapToDouble(v -> v).average();
  }
  
  public OptionalDouble getAveragePickupTimeSecs() {
    return pickupTimeSecsValues.stream().mapToDouble(v -> v).average();
  }
  
  public OptionalDouble getAverageReviewTimeSecs() {
    return reviewTimeSecsValues.stream().mapToDouble(v -> v).average();
  }
}
