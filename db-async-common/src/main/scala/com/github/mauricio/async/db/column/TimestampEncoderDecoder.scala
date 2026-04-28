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

import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.sql.Timestamp
import java.util.{Calendar, Date}

object TimestampEncoderDecoder {
  val BaseFormat   = "yyyy-MM-dd HH:mm:ss"
  val FractionSize = 6
  val Instance     = new TimestampEncoderDecoder()
}

class TimestampEncoderDecoder extends ColumnEncoderDecoder {

  import TimestampEncoderDecoder._

  private val systemZone = ZoneId.systemDefault()

  private val format = new DateTimeFormatterBuilder()
    .appendPattern(BaseFormat)
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, FractionSize, true)
    .optionalEnd()
    .optionalStart()
    .appendOffset("+HH:mm", "Z")
    .optionalEnd()
    .toFormatter()

  protected val timezonedPrinter: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern(BaseFormat)
      .appendFraction(
        ChronoField.NANO_OF_SECOND,
        FractionSize,
        FractionSize,
        true
      )
      .appendOffset("+HH:mm", "Z")
      .toFormatter()

  protected val nonTimezonedPrinter: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern(BaseFormat)
      .appendFraction(
        ChronoField.NANO_OF_SECOND,
        FractionSize,
        FractionSize,
        true
      )
      .toFormatter()

  def formatter = format

  override def decode(value: String): Any = {
    LocalDateTime.parse(value, formatter)
  }

  override def encode(value: Any): String = {
    value match {
      case t: Timestamp =>
        this.timezonedPrinter.format(t.toInstant.atZone(systemZone))
      case t: Date =>
        this.timezonedPrinter.format(t.toInstant.atZone(systemZone))
      case t: Calendar =>
        this.timezonedPrinter.format(t.toInstant.atZone(systemZone))
      case t: LocalDateTime  => this.nonTimezonedPrinter.format(t)
      case t: OffsetDateTime => this.timezonedPrinter.format(t)
      case t: Instant => this.timezonedPrinter.format(t.atZone(systemZone))
      case _          => throw new DateEncoderNotAvailableException(value)
    }
  }

}
