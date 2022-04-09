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
import java.sql.Timestamp
import java.util.{Calendar, Date}
import java.time._
import java.time.format._
import java.time.temporal._

object TimestampEncoderDecoder {
  val BaseFormat   = "yyyy-MM-dd HH:mm:ss"
  val MillisFormat = ".SSSSSS"
  val Instance     = new TimestampEncoderDecoder()
}

class TimestampEncoderDecoder extends ColumnEncoderDecoder {

  import TimestampEncoderDecoder._

  private val builder = new DateTimeFormatterBuilder()
    .appendPattern(BaseFormat)
    .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
    .appendOptional(DateTimeFormatter.ofPattern("Z"))

  private val timezonedPrinter = new DateTimeFormatterBuilder()
    .appendPattern(s"${BaseFormat}${MillisFormat}Z")
    .toFormatter

  private val nonTimezonedPrinter = new DateTimeFormatterBuilder()
    .appendPattern(s"${BaseFormat}${MillisFormat}")
    .toFormatter

  private val format = builder.toFormatter

  def formatter = format

  override def decode(value: String): Any = {
    LocalDateTime.parse(value, format)
  }

  override def encode(value: Any): String = {
    value match {
      case t: Timestamp        => this.timezonedPrinter.format(t.toInstant)
      case t: Date             => this.timezonedPrinter.format(t.toInstant)
      case t: Calendar         => this.timezonedPrinter.format(t.toInstant)
      case t: LocalDateTime    => this.nonTimezonedPrinter.format(t)
      case t: TemporalAccessor => this.timezonedPrinter.format(t)
      case _ => throw new DateEncoderNotAvailableException(value)
    }
  }

}
