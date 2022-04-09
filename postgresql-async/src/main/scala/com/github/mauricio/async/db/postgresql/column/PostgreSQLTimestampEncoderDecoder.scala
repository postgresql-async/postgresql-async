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

package com.github.mauricio.async.db.postgresql.column

import com.github.mauricio.async.db.column.ColumnEncoderDecoder
import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException
import com.github.mauricio.async.db.general.ColumnData
import com.github.mauricio.async.db.postgresql.messages.backend.PostgreSQLColumnData
import com.github.mauricio.async.db.util.Log
import io.netty.buffer.ByteBuf
import java.nio.charset.Charset
import java.sql.Timestamp
import java.util.{Calendar, Date}
import java.time._
import java.time.format._
import java.time.temporal._

object PostgreSQLTimestampEncoderDecoder extends ColumnEncoderDecoder {

  private val log = Log.getByName(this.getClass.getName)

  private val optionalTimeZone = DateTimeFormatter.ofPattern("Z")

  private val internalFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
      .appendOptional(optionalTimeZone)
      .toFormatter

  private val internalFormatterWithoutSeconds = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .appendOptional(optionalTimeZone)
    .toFormatter

  def formatter = internalFormatter

  override def decode(
    kind: ColumnData,
    value: ByteBuf,
    charset: Charset
  ): Any = {
    val bytes = new Array[Byte](value.readableBytes())
    value.readBytes(bytes)

    val text = new String(bytes, charset)

    val columnType = kind.asInstanceOf[PostgreSQLColumnData]
    LocalDateTime.parse(text, internalFormatter)
  }

  private def selectFormatter(text: String) = {
    internalFormatter
  }

  override def decode(value: String): Any =
    throw new UnsupportedOperationException(
      "this method should not have been called"
    )

  override def encode(value: Any): String = {
    value match {
      case t: Timestamp     => this.formatter.format(t.toInstant)
      case t: Date          => this.formatter.format(t.toInstant)
      case t: Calendar      => this.formatter.format(t.toInstant)
      case t: LocalDateTime => this.formatter.format(t)
      case t: ZonedDateTime => this.formatter.format(t)
      case _                => throw new DateEncoderNotAvailableException(value)
    }
  }

  override def supportsStringDecoding: Boolean = false

}
