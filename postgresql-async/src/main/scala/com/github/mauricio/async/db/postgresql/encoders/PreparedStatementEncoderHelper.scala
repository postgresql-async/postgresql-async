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

import com.github.mauricio.async.db.postgresql.messages.backend.ServerMessage
import com.github.mauricio.async.db.util.{Log, ByteBufferUtils}
import com.github.mauricio.async.db.column.ColumnEncoderRegistry
import java.nio.charset.Charset
import io.netty.buffer.{Unpooled, ByteBuf}
import scala.collection.mutable.ArrayBuffer

object PreparedStatementEncoderHelper {
  final val log = Log.get[PreparedStatementEncoderHelper]
}

trait PreparedStatementEncoderHelper {

  import PreparedStatementEncoderHelper.log

  /**
   * Writes a Bind/Describe/Execute/Sync sequence for a statement and portal
   * that share the same name. A Close for the portal is always appended.
   */
  def writeExecutePortal(
    statementIdBytes: Array[Byte],
    query: String,
    values: Seq[Any],
    encoder: ColumnEncoderRegistry,
    charset: Charset,
    writeDescribe: Boolean = false
  ): ByteBuf = writeExecutePortal(
    statementIdBytes,
    statementIdBytes,
    query,
    values,
    encoder,
    charset,
    writeDescribe,
    writeClose = true
  )

  /**
   * Writes a Bind/Describe/Execute/Sync sequence using independent statement
   * and portal names.
   *
   * Named prepared statements reuse the same bytes for both names and close the
   * portal after execution. Unnamed prepared statements use empty names and
   * skip the Close because the unnamed portal is replaced by the next
   * execution.
   */
  def writeExecutePortal(
    statementNameBytes: Array[Byte],
    portalNameBytes: Array[Byte],
    query: String,
    values: Seq[Any],
    encoder: ColumnEncoderRegistry,
    charset: Charset,
    writeDescribe: Boolean,
    writeClose: Boolean
  ): ByteBuf = {

    if (log.isDebugEnabled) {
      log.debug(
        s"Preparing execute portal to statement ($query) - values (${values
            .mkString(", ")}) - ${charset}"
      )
    }

    val bindBuffer = Unpooled.buffer(1024)

    bindBuffer.writeByte(ServerMessage.Bind)
    bindBuffer.writeInt(0)

    bindBuffer.writeBytes(portalNameBytes)
    bindBuffer.writeByte(0)
    bindBuffer.writeBytes(statementNameBytes)
    bindBuffer.writeByte(0)

    bindBuffer.writeShort(0)

    bindBuffer.writeShort(values.length)

    val decodedValues = if (log.isDebugEnabled) {
      new ArrayBuffer[String](values.size)
    } else {
      null
    }

    for (value <- values) {
      if (isNull(value)) {
        bindBuffer.writeInt(-1)

        if (log.isDebugEnabled) {
          decodedValues += null
        }
      } else {
        val encodedValue = encoder.encode(value)

        if (log.isDebugEnabled) {
          decodedValues += encodedValue
        }

        if (isNull(encodedValue)) {
          bindBuffer.writeInt(-1)
        } else {
          val content = encodedValue.getBytes(charset)
          bindBuffer.writeInt(content.length)
          bindBuffer.writeBytes(content)
        }

      }
    }

    if (log.isDebugEnabled) {
      log.debug(
        s"Executing portal - statement id (${statementNameBytes.mkString("-")}) - statement ($query) - encoded values (${decodedValues
            .mkString(", ")}) - original values (${values.mkString(", ")})"
      )
    }

    bindBuffer.writeShort(0)

    ByteBufferUtils.writeLength(bindBuffer)

    if (writeDescribe) {
      val describeLength = 1 + 4 + 1 + portalNameBytes.length + 1
      val describeBuffer = bindBuffer
      describeBuffer.writeByte(ServerMessage.Describe)
      describeBuffer.writeInt(describeLength - 1)
      describeBuffer.writeByte('P')
      describeBuffer.writeBytes(portalNameBytes)
      describeBuffer.writeByte(0)
    }

    val executeLength = 1 + 4 + portalNameBytes.length + 1 + 4
    val executeBuffer = Unpooled.buffer(executeLength)
    executeBuffer.writeByte(ServerMessage.Execute)
    executeBuffer.writeInt(executeLength - 1)
    executeBuffer.writeBytes(portalNameBytes)
    executeBuffer.writeByte(0)
    executeBuffer.writeInt(0)

    val syncBuffer = Unpooled.buffer(5)
    syncBuffer.writeByte(ServerMessage.Sync)
    syncBuffer.writeInt(4)

    if (writeClose) {
      val closeLength = 1 + 4 + 1 + portalNameBytes.length + 1
      val closeBuffer = Unpooled.buffer(closeLength)
      closeBuffer.writeByte(ServerMessage.CloseStatementOrPortal)
      closeBuffer.writeInt(closeLength - 1)
      closeBuffer.writeByte('P')
      closeBuffer.writeBytes(portalNameBytes)
      closeBuffer.writeByte(0)

      Unpooled.wrappedBuffer(bindBuffer, executeBuffer, syncBuffer, closeBuffer)
    } else {
      Unpooled.wrappedBuffer(bindBuffer, executeBuffer, syncBuffer)
    }

  }

  def isNull(value: Any): Boolean = value == null || value == None

}
