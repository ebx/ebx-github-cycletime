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

package com.echobox.github.cycletime.providers.kohsuke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHUser;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests of the PRAnalyser
 * @author MarcF
 */
@ExtendWith(MockitoExtension.class)
public class UserKohsukeTest {
  
  @Mock
  private GHUser user;

  @Test
  public void testGetSafeUserNameStrWithName() throws Exception {
    when(user.getName()).thenReturn("TestName");

    UserKohsuke userKohsuke = new UserKohsuke(user);
    assertEquals("TestName", userKohsuke.getName());
    
    verify(user, Mockito.times(0)).getLogin();
  }
  
  @Test
  public void testGetSafeUserNameStrWithLoginOnly() throws Exception {
    when(user.getName()).thenReturn(null);
    when(user.getLogin()).thenReturn("LoginId");
    
    UserKohsuke userKohsuke = new UserKohsuke(user);
    
    assertEquals("LoginId", userKohsuke.getName());
  }
}
