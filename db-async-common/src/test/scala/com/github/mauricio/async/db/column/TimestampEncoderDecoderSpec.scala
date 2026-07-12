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

package com.github.mauricio.async.db.column

import com.github.mauricio.async.db.Spec
import java.sql.Timestamp
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.{OffsetDateTime, ZoneId, ZoneOffset}
import java.util.Calendar

class TimestampEncoderDecoderSpec extends Spec {

  val encoder  = TimestampEncoderDecoder.Instance
  val dateTime =
    OffsetDateTime.of(2013, 12, 27, 8, 40, 50, 800000000, ZoneOffset.UTC)
  val systemZone = ZoneId.systemDefault()
  val formatter  = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
    .appendOffset("+HH:mm", "Z")
    .toFormatter()

  val result             = "2013-12-27 08:40:50.800000"
  val resultWithTimezone =
    formatter.format(dateTime.toInstant.atZone(systemZone))
  val resultOffsetDateTime = formatter.format(dateTime)

  "decoder" - {
    "should print a timestamp" in {
      val timestamp = Timestamp.from(dateTime.toInstant)
      encoder.encode(timestamp) === resultWithTimezone
    }

    "should print a LocalDateTime" in {
      encoder.encode(dateTime.toLocalDateTime) === result
    }

    "should print a date" in {
      encoder.encode(
        java.util.Date.from(dateTime.toInstant)
      ) === resultWithTimezone
    }

    "should print a calendar" in {
      val calendar = Calendar.getInstance()
      calendar.setTime(java.util.Date.from(dateTime.toInstant))
      encoder.encode(calendar) === resultWithTimezone
    }

    "should print an offset datetime" in {
      encoder.encode(dateTime) === resultOffsetDateTime
    }
  }
}
