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

package com.github.mauricio.async.db.mysql.decoder

import io.netty.buffer.ByteBuf
import com.github.mauricio.async.db.mysql.message.server.{
  ErrorMessage,
  ServerMessage
}
import com.github.mauricio.async.db.util.ChannelWrapper.bufferToWrapper
import java.nio.charset.Charset
import scala.language.implicitConversions

class ErrorDecoder(charset: Charset) extends MessageDecoder {

  def decode(buffer: ByteBuf): ServerMessage = {

    new ErrorMessage(
      buffer.readShortLE(),
      buffer.readFixedString(6, charset),
      buffer.readUntilEOF(charset)
    )

  }

}
