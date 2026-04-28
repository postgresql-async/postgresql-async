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

import java.time.OffsetTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object TimeWithTimezoneEncoderDecoder extends TimeEncoderDecoder {

  protected override val format: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern("HH:mm:ss")
      .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
      .appendOffset("+HH:mm", "Z")
      .toFormatter()

  protected override val printer: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern("HH:mm:ss")
      .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
      .appendPattern("XXX")
      .toFormatter()

  override def formatter = format

  override def decode(value: String): Any =
    OffsetTime.parse(value, formatter)

  override def encode(value: Any): String =
    printer.format(value.asInstanceOf[OffsetTime])

}
