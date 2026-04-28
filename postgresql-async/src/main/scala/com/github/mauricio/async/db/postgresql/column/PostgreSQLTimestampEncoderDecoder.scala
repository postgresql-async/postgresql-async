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
import com.github.mauricio.async.db.postgresql.util.DateTimeParserHelper
import io.netty.buffer.ByteBuf
import java.nio.charset.Charset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.sql.Timestamp
import java.util.{Calendar, Date}

object PostgreSQLTimestampEncoderDecoder extends ColumnEncoderDecoder {

  private val log = Log.getByName(this.getClass.getName)

  private val systemZone = ZoneId.systemDefault()

  val formatter = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
    .optionalStart()
    .appendOffset("+HH:mm", "Z")
    .optionalEnd()
    .toFormatter()

  override def decode(
    kind: ColumnData,
    value: ByteBuf,
    charset: Charset
  ): Any = {
    val columnType = kind.asInstanceOf[PostgreSQLColumnData]

    columnType.dataType match {
      case ColumnTypes.Timestamp | ColumnTypes.TimestampArray =>
        DateTimeParserHelper.fastParseLocalDateTime(value, charset).getOrElse {
          // Fallback to string parsing if ByteBuf parsing fails
          val bytes = new Array[Byte](value.readableBytes())
          value.readBytes(bytes)
          val text = new String(bytes, charset)
          DateTimeParserHelper.parseLocalDateTime(text)
        }
      case ColumnTypes.TimestampWithTimezoneArray =>
        DateTimeParserHelper.fastParseOffsetDateTime(value).getOrElse {
          // Fallback to string parsing if ByteBuf parsing fails
          val bytes = new Array[Byte](value.readableBytes())
          value.readBytes(bytes)
          val text = new String(bytes, charset)
          DateTimeParserHelper.parseOffsetDateTime(text)
        }
      case ColumnTypes.TimestampWithTimezone =>
        DateTimeParserHelper.fastParseOffsetDateTime(value).getOrElse {
          // Fallback to string parsing if ByteBuf parsing fails
          val bytes = new Array[Byte](value.readableBytes())
          value.readBytes(bytes)
          val text = new String(bytes, charset)
          DateTimeParserHelper.parseOffsetDateTime(text)
        }
    }
  }

  override def decode(value: String): Any =
    throw new UnsupportedOperationException(
      "this method should not have been called"
    )

  override def encode(value: Any): String = {
    value match {
      case t: Timestamp => this.formatter.format(t.toInstant.atZone(systemZone))
      case t: Date      => this.formatter.format(t.toInstant.atZone(systemZone))
      case t: Calendar  => this.formatter.format(t.toInstant.atZone(systemZone))
      case t: LocalDateTime  => this.formatter.format(t)
      case t: OffsetDateTime => this.formatter.format(t)
      case t: Instant        => this.formatter.format(t.atZone(systemZone))
      case _ => throw new DateEncoderNotAvailableException(value)
    }
  }

  override def supportsStringDecoding: Boolean = false

}
