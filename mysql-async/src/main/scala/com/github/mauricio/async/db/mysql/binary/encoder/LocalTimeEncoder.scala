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
import java.time.LocalTime
import com.github.mauricio.async.db.mysql.column.ColumnTypes

object LocalTimeEncoder extends BinaryEncoder {
  def encode(value: Any, buffer: ByteBuf): Unit = {
    val time = value.asInstanceOf[LocalTime]

    val micros    = time.getNano / 1000
    val hasMicros = micros != 0

    if (hasMicros) {
      buffer.writeByte(12)
    } else {
      buffer.writeByte(8)
    }

    if (time.toNanoOfDay > 0) {
      buffer.writeByte(0)
    } else {
      buffer.writeByte(1)
    }

    buffer.writeInt(0)

    buffer.writeByte(time.getHour)
    buffer.writeByte(time.getMinute)
    buffer.writeByte(time.getSecond)

    if (hasMicros) {
      buffer.writeInt(micros)
    }

  }

  def encodesTo: Int = ColumnTypes.FIELD_TYPE_TIME
}
