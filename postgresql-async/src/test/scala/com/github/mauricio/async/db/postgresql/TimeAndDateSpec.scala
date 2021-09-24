/*
 * Copyright 2013 Maurício Linhares
 *
 * Maurício Linhares licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.mauricio.async.db.postgresql

import org.joda.time._
import java.time.{Period, Duration => JavaDuration}

import scala.concurrent.duration._

import org.specs2.mutable.Specification

class TimeAndDateSpec extends Specification with DatabaseTestHelper {

  "when processing times and dates" should {

    "support a time object" in {

      withHandler { handler =>
        val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment time NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""

        executeDdl(handler, create)
        executePreparedStatement(
          handler,
          "INSERT INTO messages (moment) VALUES (?)",
          Array[Any](new LocalTime(4, 5, 6))
        )

        val rows =
          executePreparedStatement(handler, "select * from messages").rows.get

        val time = rows(0)("moment").asInstanceOf[LocalTime]

        time.getHourOfDay === 4
        time.getMinuteOfHour === 5
        time.getSecondOfMinute === 6
      }

    }

    "support a time object with microseconds" in {

      withHandler { handler =>
        val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment time(6) NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""

        executeDdl(handler, create)
        executePreparedStatement(
          handler,
          "INSERT INTO messages (moment) VALUES (?)",
          Array[Any](new LocalTime(4, 5, 6, 134))
        )

        val rows =
          executePreparedStatement(handler, "select * from messages").rows.get

        val time = rows(0)("moment").asInstanceOf[LocalTime]

        time.getHourOfDay === 4
        time.getMinuteOfHour === 5
        time.getSecondOfMinute === 6
        time.getMillisOfSecond === 134
      }

    }

    "support a time with timezone object" in {

      pending("need to find a way to implement this")

      withHandler { handler =>
        val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment time with time zone NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""

        executeDdl(handler, create)
        executeQuery(
          handler,
          "INSERT INTO messages (moment) VALUES ('04:05:06 -3:00')"
        )

        val rows =
          executePreparedStatement(handler, "select * from messages").rows.get

        val time = rows(0)("moment").asInstanceOf[LocalTime]

        time.getHourOfDay === 4
        time.getMinuteOfHour === 5
        time.getSecondOfMinute === 6
      }

    }

    "support timestamp with timezone" in {
      withHandler { handler =>
        val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment timestamp with time zone NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""

        executeDdl(handler, create)
        executeQuery(
          handler,
          "INSERT INTO messages (moment) VALUES ('1999-01-08 04:05:06 -3:00')"
        )
        val rows =
          executePreparedStatement(handler, "SELECT * FROM messages").rows.get

        rows.length === 1

        val dateTime = rows(0)("moment").asInstanceOf[DateTime]

        // Note: Since this assertion depends on Brazil locale, I think epoch time assertion is preferred
        // dateTime.getZone.toTimeZone.getRawOffset === -10800000
        dateTime.getMillis === 915779106000L
      }
    }

    "support timestamp with timezone and microseconds" in {

      foreach(1.until(6)) { index =>
        withHandler { handler =>
          val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment timestamp(%d) with time zone NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )""".format(index)

          executeDdl(handler, create)

          val seconds = (index.toString * index).toLong

          executeQuery(
            handler,
            "INSERT INTO messages (moment) VALUES ('1999-01-08 04:05:06.%d -3:00')"
              .format(seconds)
          )
          val rows =
            executePreparedStatement(handler, "SELECT * FROM messages").rows.get

          rows.length === 1

          val dateTime = rows(0)("moment").asInstanceOf[DateTime]

          // Note: Since this assertion depends on Brazil locale, I think epoch time assertion is preferred
          // dateTime.getZone.toTimeZone.getRawOffset === -10800000
          dateTime.getMillis must be_>=(915779106000L)
          dateTime.getMillis must be_<(915779107000L)
        }
      }
    }

    "support current_timestamp with timezone" in {
      withHandler { handler =>
        val millis = System.currentTimeMillis

        val create = """CREATE TEMP TABLE messages
                         (
                           id bigserial NOT NULL,
                           moment timestamp with time zone NOT NULL,
                           CONSTRAINT bigserial_column_pkey PRIMARY KEY (id )
                         )"""

        executeDdl(handler, create)
        executeQuery(
          handler,
          "INSERT INTO messages (moment) VALUES (current_timestamp)"
        )
        val rows =
          executePreparedStatement(handler, "SELECT * FROM messages").rows.get

        rows.length === 1

        val dateTime = rows(0)("moment").asInstanceOf[DateTime]

        dateTime.getMillis must beCloseTo(millis, 500)
      }
    }

    "handle sending a time with timezone and return a LocalDateTime for a timestamp without timezone column" in {

      withTimeHandler { conn =>
        val date = new DateTime(2190319)

        executePreparedStatement(conn, "CREATE TEMP TABLE TEST(T TIMESTAMP)")
        executePreparedStatement(
          conn,
          "INSERT INTO TEST(T) VALUES(?)",
          Array(date)
        )
        val result = executePreparedStatement(conn, "SELECT T FROM TEST")
        val date2  = result.rows.get.head(0)
        date2 === date.toDateTime(DateTimeZone.UTC).toLocalDateTime
      }

    }

    "supports sending a local date and later a date time object for the same field" in {

      withTimeHandler { conn =>
        val date = new LocalDate(2016, 3, 5)

        executePreparedStatement(conn, "CREATE TEMP TABLE TEST(T TIMESTAMP)")
        executePreparedStatement(
          conn,
          "INSERT INTO TEST(T) VALUES(?)",
          Array(date)
        )
        val result = executePreparedStatement(
          conn,
          "SELECT T FROM TEST WHERE T  = ?",
          Array(date)
        )
        result.rows.get.size === 1

        val dateTime = new LocalDateTime(2016, 3, 5, 0, 0, 0, 0)
        val dateTimeResult = executePreparedStatement(
          conn,
          "SELECT T FROM TEST WHERE T  = ?",
          Array(dateTime)
        )
        dateTimeResult.rows.get.size === 1
      }

    }

    "handle sending a LocalDateTime and return a LocalDateTime for a timestamp without timezone column" in {

      withTimeHandler { conn =>
        val date1 = new LocalDateTime(2190319)

        await(conn.sendPreparedStatement("CREATE TEMP TABLE TEST(T TIMESTAMP)"))
        await(
          conn
            .sendPreparedStatement("INSERT INTO TEST(T) VALUES(?)", Seq(date1))
        )
        val result = await(conn.sendPreparedStatement("SELECT T FROM TEST"))
        val date2  = result.rows.get.head(0)

        date2 === date1
      }

    }

    "handle sending a date with timezone and retrieving the date with the same time zone" in {

      withTimeHandler { conn =>
        val date1 = new DateTime(2190319)

        await(
          conn.sendPreparedStatement(
            "CREATE TEMP TABLE TEST(T TIMESTAMP WITH TIME ZONE)"
          )
        )
        await(
          conn
            .sendPreparedStatement("INSERT INTO TEST(T) VALUES(?)", Seq(date1))
        )
        val result = await(conn.sendPreparedStatement("SELECT T FROM TEST"))
        val date2  = result.rows.get.head(0)

        date2 === date1
      }
    }

    "support intervals" in {
      withHandler { handler =>
        executeDdl(
          handler,
          "CREATE TEMP TABLE intervals (id Int, duration interval NOT NULL)"
        )

        // Scala Duration P1Y2M4DT5H6M7S
        val p1 =
          365.days
            .plus((2 * 30).days)
            .plus(4.days)
            .plus(5.hours)
            .plus(6.minutes)
            .plus(7.seconds)

        // Java Time Duration P1D2H3M4S
        val p2 =
          JavaDuration
            .ofDays(1)
            .plusHours(2)
            .plusMinutes(3)
            .plusSeconds(4)

        // Java Time Period P1Y2M3D
        val p3 = Period.ofYears(1).plusMonths(2).plusDays(3)

        executePreparedStatement(
          handler,
          "INSERT INTO intervals (id, duration) VALUES (?, ?)",
          Array(1, p1)
        )
        executePreparedStatement(
          handler,
          "INSERT INTO intervals (id, duration) VALUES (?, ?)",
          Array(2, p2)
        )
        executePreparedStatement(
          handler,
          "INSERT INTO intervals (id, duration) VALUES (?, ?)",
          Array(3, p3)
        )

        val rows =
          executeQuery(
            handler,
            "SELECT duration FROM intervals order by id"
          ).rows.get

        rows.length === 3

        rows(0)(0) === p1

        rows(1)(0) === p2.getSeconds.seconds

        val p30 =
          (p3.getYears * 365).days + (p3.getMonths * 30).days + p3.getDays.days

        rows(2)(0) === p30
      }
    }

  }

}
