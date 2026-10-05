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

package com.github.mauricio.async.db.postgresql.encoders

import com.github.mauricio.async.db.column.ColumnEncoderRegistry
import com.github.mauricio.async.db.postgresql.messages.backend.ServerMessage
import com.github.mauricio.async.db.postgresql.messages.frontend.{
  ClientMessage,
  UnnamedPreparedStatementMessage
}
import com.github.mauricio.async.db.util.{ByteBufferUtils, Log}
import io.netty.buffer.{ByteBuf, Unpooled}

import java.nio.charset.Charset

object UnnamedPreparedStatementEncoder {
  val log = Log.get[UnnamedPreparedStatementEncoder]
}

/**
 * Encodes an unnamed prepared statement as a Parse message followed by an
 * unnamed Bind/Describe/Execute/Sync sequence.
 *
 * No Close message is written: the unnamed statement and portal are replaced by
 * the next unnamed execution, so an explicit deallocation is unnecessary.
 */
class UnnamedPreparedStatementEncoder(
  charset: Charset,
  encoder: ColumnEncoderRegistry
) extends Encoder
    with PreparedStatementEncoderHelper {

  import UnnamedPreparedStatementEncoder.log

  override def encode(message: ClientMessage): ByteBuf = {
    val m           = message.asInstanceOf[UnnamedPreparedStatementMessage]
    val columnCount = m.valueTypes.size
    val parseBuffer = Unpooled.buffer(1024)

    parseBuffer.writeByte(ServerMessage.Parse)
    parseBuffer.writeInt(0)

    // The empty string sentinel selects the unnamed statement.
    parseBuffer.writeByte(0)
    parseBuffer.writeBytes(m.query.getBytes(charset))
    parseBuffer.writeByte(0)

    parseBuffer.writeShort(columnCount)

    if (log.isDebugEnabled) {
      log.debug(
        s"Opening unnamed query (${m.query}) - selected types (${m.valueTypes
            .mkString(", ")}) - values (${m.values.mkString(", ")})"
      )
    }

    for (kind <- m.valueTypes) {
      parseBuffer.writeInt(kind)
    }

    ByteBufferUtils.writeLength(parseBuffer)

    val executeBuffer = writeExecutePortal(
      Array.emptyByteArray,
      Array.emptyByteArray,
      m.query,
      m.values,
      encoder,
      charset,
      writeDescribe = true,
      writeClose = false
    )

    Unpooled.wrappedBuffer(parseBuffer, executeBuffer)
  }

}
