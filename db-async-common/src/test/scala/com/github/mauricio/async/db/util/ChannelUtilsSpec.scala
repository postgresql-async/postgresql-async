package com.github.mauricio.async.db.util

import com.github.mauricio.async.db.Spec
import io.netty.util.CharsetUtil
import io.netty.buffer.Unpooled

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

class ChannelUtilsSpec extends Spec {

  val charset = CharsetUtil.UTF_8

  "utils" - {

    "correctly write and read a string" in {
      val content = "some text"
      val buffer  = Unpooled.buffer()

      ByteBufferUtils.writeCString(content, buffer, charset)

      ByteBufferUtils.readCString(buffer, charset) mustEqual content
      buffer.readableBytes() mustEqual 0
    }

    "correctly read the buggy MySQL EOF string when there is an EOF" in {
      val content = "some text"
      val buffer  = Unpooled.buffer()

      ByteBufferUtils.writeCString(content, buffer, charset)

      ByteBufferUtils.readUntilEOF(buffer, charset) mustEqual content
      buffer.readableBytes() mustEqual 0
    }

    "correctly read the buggy MySQL EOF string when there is no EOF" in {

      val content = "some text"
      val buffer  = Unpooled.buffer()

      buffer.writeBytes(content.getBytes(charset))

      ByteBufferUtils.readUntilEOF(buffer, charset) mustEqual content
      buffer.readableBytes() mustEqual 0

    }

  }

}
