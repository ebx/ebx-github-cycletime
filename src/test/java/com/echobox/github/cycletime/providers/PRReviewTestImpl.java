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

package com.echobox.github.cycletime.providers;

import com.echobox.github.cycletime.analyse.PRReviewState;

import java.util.Date;

/**
 * A test implementation of the PRReview interface
 * @author MarcF
 */
public class PRReviewTestImpl implements PRReview {
  
  private final PRReviewState state;
  private final Date createdAt;
  private final User user;
  
  public PRReviewTestImpl(PRReviewState state, Date createdAt, String userName) {
    this.state = state;
    this.createdAt = createdAt;
    this.user = new UserTestImpl(userName);
  }
  
  @Override
  public PRReviewState getState() {
    return state;
  }
  
  @Override
  public Date getReviewCreatedAt() {
    return createdAt;
  }
  
  @Override
  public User getReviewedByUser() {
    return user;
  }
}
