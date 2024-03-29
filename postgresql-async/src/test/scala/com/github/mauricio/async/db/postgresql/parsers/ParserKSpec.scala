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
  ProcessData
}
import com.github.mauricio.async.db.Spec
import io.netty.buffer.Unpooled

class ParserKSpec extends Spec {

  val parser = BackendKeyDataParser

  "parserk" - {

    "correctly parse the message" in {

      val buffer = Unpooled.buffer()
      buffer.writeInt(10)
      buffer.writeInt(20)

      val data = parser.parseMessage(buffer).asInstanceOf[ProcessData]

      data.kind === ServerMessage.BackendKeyData
      data.processId === 10
      data.secretKey === 20

    }

  }

}
