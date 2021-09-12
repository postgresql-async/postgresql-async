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

import java.time.{Instant, LocalDate}
import com.github.mauricio.async.db.mysql.column.ColumnTypes

object SQLDateEncoder extends BinaryEncoder {
  def encode(value: Any, buffer: ByteBuf): Unit = {
    val date = value.asInstanceOf[java.sql.Date]

    LocalDateEncoder.encode(
      LocalDate.from(Instant.ofEpochMilli(date.getTime)),
      buffer
    )
  }

  def encodesTo: Int = ColumnTypes.FIELD_TYPE_DATE
}
