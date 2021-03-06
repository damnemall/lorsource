/*
 * Copyright 1998-2010 Linux.org.ru
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.org.linux.site;

import java.net.UnknownHostException;
import java.sql.*;
import java.util.Date;

import org.xbill.DNS.TextParseException;

import ru.org.linux.util.DNSBLClient;

public class IPBlockInfo {
  private final String reason;
  private final Timestamp banDate;
  private final Timestamp originalDate;
  private final User moderator;

  private IPBlockInfo(Connection db, ResultSet rs) throws SQLException {
    reason = rs.getString("reason");
    banDate = rs.getTimestamp("ban_date");
    originalDate = rs.getTimestamp("date");
    int moderatorId = rs.getInt("mod_id");
    try {
      moderator = User.getUser(db, moderatorId);
    } catch (UserNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static IPBlockInfo getBlockInfo(Connection db, String addr) throws SQLException {
    PreparedStatement st = null;

    try {
      st= db.prepareStatement("SELECT reason, ban_date, date, mod_id FROM b_ips WHERE ip = ?::inet");

      st.setString(1, addr);
  
      ResultSet rs = st.executeQuery();

      if (rs.next()) {
        return new IPBlockInfo(db, rs);
      } else {
	return null;
      }
    } finally {
      if (st!=null) {
    	st.close();
      }
    }
  }

  public boolean isBlocked() {
    return banDate == null || banDate.after(new Date());
  }

  public void checkBlock() throws AccessViolationException {
    if (isBlocked()) {
      throw new AccessViolationException("Постинг заблокирован: "+reason);
    }
  }

  public Timestamp getOriginalDate() {
    return originalDate;
  }

  public Timestamp getBanDate() {
    return banDate;
  }

  public String getReason() {
    return reason;
  }

  public User getModerator() {
    return moderator;
  }

  public static boolean getTor(String addr) throws TextParseException, UnknownHostException {
    DNSBLClient dnsbl = new DNSBLClient("tor.ahbl.org");
    return (dnsbl.checkIP(addr));
  }

  public static void checkBlockIP(Connection db, String addr) throws AccessViolationException, SQLException, UnknownHostException, TextParseException {
    if (getTor(addr)) {
      throw new AccessViolationException("Постинг заблокирован: tor.ahbl.org");      
    }

    IPBlockInfo block = getBlockInfo(db, addr);

    if (block == null) {
      return;
    }

    block.checkBlock();
  }
}
