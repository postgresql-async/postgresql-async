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

package com.github.mauricio.async.db.postgresql.parsers

import com.github.mauricio.async.db.postgresql.messages.backend.{
  ServerMessage,
  ParameterStatusMessage
}
import java.nio.charset.Charset
import com.github.mauricio.async.db.Spec
import io.netty.buffer.Unpooled
import io.netty.util.CharsetUtil

class ParserSSpec extends Spec {

  val parser = new ParameterStatusParser(CharsetUtil.UTF_8)

  "ParameterStatusParser" - {

    "correctly parse a config pair" in {

      val key   = "application-name"
      val value = "my-cool-application"

      val buffer = Unpooled.buffer()

      buffer.writeBytes(key.getBytes(Charset.forName("UTF-8")))
      buffer.writeByte(0)
      buffer.writeBytes(value.getBytes(Charset.forName("UTF-8")))
      buffer.writeByte(0)

      val content =
        this.parser.parseMessage(buffer).asInstanceOf[ParameterStatusMessage]

      content.key mustEqual key
      content.value mustEqual value
      content.kind mustEqual ServerMessage.ParameterStatus
      buffer.readerIndex() mustEqual buffer.writerIndex()
    }

  }

}
