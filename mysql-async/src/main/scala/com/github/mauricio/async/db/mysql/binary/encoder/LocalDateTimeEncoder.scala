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

package com.github.mauricio.async.db.mysql.binary.encoder

import io.netty.buffer.ByteBuf
import com.github.mauricio.async.db.mysql.column.ColumnTypes
import java.time._
import java.time.temporal._

object LocalDateTimeEncoder extends BinaryEncoder {

  def encode(value: Any, buffer: ByteBuf): Unit = {
    val instant = value.asInstanceOf[LocalDateTime]

    val microSeconds = instant.get(ChronoField.MICRO_OF_SECOND)
    val hasMicro     = microSeconds != 0

    if (hasMicro) {
      buffer.writeByte(11)
    } else {
      buffer.writeByte(7)
    }

    buffer.writeShort(instant.getYear)
    buffer.writeByte(instant.getMonthValue)
    buffer.writeByte(instant.getDayOfMonth)
    buffer.writeByte(instant.getHour)
    buffer.writeByte(instant.getMinute)
    buffer.writeByte(instant.getSecond)

    if (hasMicro) {
      buffer.writeInt(microSeconds)
    }

  }

  def encodesTo: Int = ColumnTypes.FIELD_TYPE_TIMESTAMP
}
