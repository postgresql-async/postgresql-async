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

import com.github.mauricio.async.db.Spec
import org.joda.time._

class TimeAndDateSpec extends Spec with DatabaseTestHelper {

  "when processing times and dates" - {

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

        time.getHourOfDay mustEqual 4
        time.getMinuteOfHour mustEqual 5
        time.getSecondOfMinute mustEqual 6
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

        time.getHourOfDay mustEqual 4
        time.getMinuteOfHour mustEqual 5
        time.getSecondOfMinute mustEqual 6
        time.getMillisOfSecond mustEqual 134
      }

    }

    "support a time with timezone object" in {

      pending

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

        time.getHourOfDay mustEqual 4
        time.getMinuteOfHour mustEqual 5
        time.getSecondOfMinute mustEqual 6
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

        rows.length mustEqual 1

        val dateTime = rows(0)("moment").asInstanceOf[DateTime]

        // Note: Since this assertion depends on Brazil locale, I think epoch time assertion is preferred
        // dateTime.getZone.toTimeZone.getRawOffset mustEqual -10800000
        dateTime.getMillis mustEqual 915779106000L
      }
    }

    "support timestamp with timezone and microseconds" in {

      forAll(1.until(6).toList) { index =>
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

          rows.length mustEqual 1

          val dateTime = rows(0)("moment").asInstanceOf[DateTime]

          // Note: Since this assertion depends on Brazil locale, I think epoch time assertion is preferred
          // dateTime.getZone.toTimeZone.getRawOffset mustEqual -10800000
          dateTime.getMillis must be >= (915779106000L)
          dateTime.getMillis must be < (915779107000L)
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

        rows.length mustEqual 1

        val dateTime = rows(0)("moment").asInstanceOf[DateTime]

        dateTime.getMillis must ===(millis +- 500)
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
        date2 mustEqual date.toDateTime(DateTimeZone.UTC).toLocalDateTime
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
        result.rows.get.size mustEqual 1

        val dateTime = new LocalDateTime(2016, 3, 5, 0, 0, 0, 0)
        val dateTimeResult = executePreparedStatement(
          conn,
          "SELECT T FROM TEST WHERE T  = ?",
          Array(dateTime)
        )
        dateTimeResult.rows.get.size mustEqual 1
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

        date2 mustEqual date1
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

        date2 mustEqual date1
      }
    }

    "support intervals" in {
      withHandler { handler =>
        executeDdl(
          handler,
          "CREATE TEMP TABLE intervals (duration interval NOT NULL)"
        )

        val p =
          new Period(1, 2, 0, 4, 5, 6, 7, 8) /* postgres normalizes weeks */
        executePreparedStatement(
          handler,
          "INSERT INTO intervals (duration) VALUES (?)",
          Array(p)
        )
        val rows =
          executeQuery(handler, "SELECT duration FROM intervals").rows.get

        rows.length mustEqual 1

        rows(0)(0) mustEqual p
      }
    }

  }

}
