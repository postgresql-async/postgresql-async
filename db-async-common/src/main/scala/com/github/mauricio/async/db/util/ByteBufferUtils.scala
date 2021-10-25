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

package com.github.mauricio.async.db.util

import java.nio.charset.Charset
import java.nio.ByteOrder
import io.netty.buffer.{Unpooled, ByteBuf}

object ByteBufferUtils {

  def writeLength(buffer: ByteBuf): Unit = {
    val length = buffer.writerIndex() - 1
    buffer.markWriterIndex()
    buffer.writerIndex(1)
    buffer.writeInt(length)
    buffer.resetWriterIndex()

  }

  def writeCString(content: String, b: ByteBuf, charset: Charset): Unit = {
    b.writeBytes(content.getBytes(charset))
    b.writeByte(0)
  }

  def writeSizedString(content: String, b: ByteBuf, charset: Charset): Unit = {
    val bytes = content.getBytes(charset)
    b.writeByte(bytes.length)
    b.writeBytes(bytes)
  }

  def readCString(b: ByteBuf, charset: Charset): String = {
    b.markReaderIndex()
    var byte: Byte = 0.toByte
    var count      = 1
    byte = b.readByte
    while (byte != 0.toByte) {
      count = count + 1
      byte = b.readByte()
    }

    b.resetReaderIndex()

    val result = b.toString(b.readerIndex(), count - 1, charset)

    b.readerIndex(b.readerIndex() + count)

    result
  }

  def readUntilEOF(b: ByteBuf, charset: Charset): String = {
    if (b.readableBytes() == 0) {
      return ""
    }

    b.markReaderIndex()

    var byte: Byte = -1
    var count      = 0
    var offset     = 1

    while (byte != 0) {
      if (b.readableBytes() > 0) {
        byte = b.readByte()
        count += 1
      } else {
        byte = 0
        offset = 0
      }
    }

    b.resetReaderIndex()

    val result = b.toString(b.readerIndex(), count - offset, charset)

    b.readerIndex(b.readerIndex() + count)

    result
  }

  def read3BytesInt(b: ByteBuf): Int = {
    (b.readByte() & 0xff) | ((b.readByte() & 0xff) << 8) | ((b
      .readByte() & 0xff) << 16)
  }

  def write3BytesInt(b: ByteBuf, value: Int): Unit = {
    b.writeByte(value & 0xff)
    b.writeByte(value >>> 8)
    b.writeByte(value >>> 16)
  }

  def writePacketLength(buffer: ByteBuf, sequence: Int = 1): Unit = {
    val length = buffer.writerIndex() - 4
    buffer.markWriterIndex()
    buffer.writerIndex(0)

    write3BytesInt(buffer, length)
    buffer.writeByte(sequence)

    buffer.resetWriterIndex()
  }

  def packetBuffer(estimate: Int = 1024): ByteBuf = {
    val buffer = mysqlBuffer(estimate)

    buffer.writeInt(0)

    buffer
  }

  def mysqlBuffer(estimate: Int = 1024): ByteBuf = {
    Unpooled.buffer(estimate).order(ByteOrder.LITTLE_ENDIAN)
  }

}
