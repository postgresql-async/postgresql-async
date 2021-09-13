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

import org.specs2.mutable.Specification

import java.sql.Timestamp
import java.time._
import java.time.format.DateTimeFormatter
import java.util.{Calendar, Date}

class TimestampEncoderDecoderSpec extends Specification {

  val encoder = TimestampEncoderDecoder.Instance
  val dateTime: LocalDateTime =
    LocalDateTime.of(LocalDate.of(2013, 12, 27), LocalTime.of(8, 40, 50, 800))

  val result             = "2013-12-27 08:40:50.800000"
  val formatter          = DateTimeFormatter.ISO_ZONED_DATE_TIME
  val resultWithTimezone = formatter.format(dateTime)

  "decoder" should {

    "should print a timestamp" in {
      val timestamp =
        new Timestamp(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli)
      encoder.encode(timestamp) === resultWithTimezone
    }

    "should print a LocalDateTime" in {
      encoder.encode(dateTime) === result
    }

    "should print a date" in {
      encoder.encode(
        Date.from(dateTime.toInstant(ZoneOffset.UTC))
      ) === resultWithTimezone
    }

    "should print a calendar" in {
      val calendar = Calendar.getInstance()
      calendar.setTimeInMillis(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli)
      encoder.encode(calendar) === resultWithTimezone
    }

  }

}
