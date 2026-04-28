/*
 * Copyright 2013 Maurício Linhares
 * Copyright 2013 Dylan Simon
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

package com.github.mauricio.async.db.postgresql.column

import java.time.{Duration, Period}

import com.github.mauricio.async.db.Spec

class IntervalSpec extends Spec {

  "interval encoder/decoder" - {

    def decode(s: String): Any = PostgreSQLIntervalEncoderDecoder.decode(s)
    def encode(i: Any): String = PostgreSQLIntervalEncoderDecoder.encode(i)

    "leave interval decoding as raw text" in {
      decode(
        "1 year 2 mons 3 days 04:05:06"
      ) === "1 year 2 mons 3 days 04:05:06"
    }

    "encode java.time.Period values as ISO-8601 intervals" in {
      encode(Period.of(1, 2, 3)) === "P1Y2M3D"
    }

    "encode java.time.Duration values as ISO-8601 intervals" in {
      encode(
        Duration.ofDays(3).plusHours(4).plusMinutes(5).plusSeconds(6)
      ) === "PT76H5M6S"
    }
  }

}
