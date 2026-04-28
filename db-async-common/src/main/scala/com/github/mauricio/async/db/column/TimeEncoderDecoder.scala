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

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object TimeEncoderDecoder {
  val Instance = new TimeEncoderDecoder()
}

class TimeEncoderDecoder extends ColumnEncoderDecoder {

  protected val format: DateTimeFormatter = new DateTimeFormatterBuilder()
    .appendPattern("HH:mm:ss")
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, true)
    .optionalEnd()
    .toFormatter()

  protected val printer: DateTimeFormatter = new DateTimeFormatterBuilder()
    .appendPattern("HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
    .toFormatter()

  def formatter = format

  override def decode(value: String): Any =
    LocalTime.parse(value, formatter)

  override def encode(value: Any): String =
    this.printer.format(value.asInstanceOf[LocalTime])

}
