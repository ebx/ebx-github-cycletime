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

import com.echobox.github.cycletime.providers.User;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHUser;

import java.io.IOException;

/**
 * A User implementation using the Kohsuke library
 * @author MarcF
 */
public class UserKohsuke implements User {
  
  private final GHUser user;
  
  public UserKohsuke(GHUser user) {
    this.user = user;
  }

  @Override
  public String getName() {
    return getSafeUserNameStr(user);
  }
  
  /**
   * Not all GHUser have a name set so we should fail over to the login id
   * @param user The original user
   * @return A safe username string to avoid blanks and nulls
   */
  private static String getSafeUserNameStr(GHUser user) {
    try {
      if (StringUtils.isEmpty(user.getName())) {
        if (user.getLogin() == null) {
          throw new IllegalStateException("Idiot check - User did not have a login?");
        } else {
          return user.getLogin();
        }
      } else {
        return user.getName();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to determine safe user name for login "
          + user.getLogin(), e);
    }
  }
}

