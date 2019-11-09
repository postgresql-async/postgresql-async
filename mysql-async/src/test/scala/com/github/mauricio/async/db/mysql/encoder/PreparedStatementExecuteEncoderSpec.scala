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

package com.github.mauricio.async.db.mysql.encoder

import com.github.mauricio.async.db.mysql.binary.BinaryRowEncoder
import io.netty.util.CharsetUtil
import org.specs2.mutable.Specification

class PreparedStatementExecuteEncoderSpec extends Specification {

  val encoder = new PreparedStatementExecuteEncoder(
    new BinaryRowEncoder(CharsetUtil.UTF_8)
  )

  "binary row encoder" should {

    "encode Some(value) like value" in {
      val actual   = encoder.encodeValues(List(Some(1L), Some("foo")), Set(0, 1))
      val expected = encoder.encodeValues(List(1L, "foo"), Set(0, 1))

      actual mustEqual expected

    }

    "encode None as null" in {
      val actual   = encoder.encodeValues(List(None), Set(0))
      val expected = encoder.encodeValues(List(null), Set(0))

      actual mustEqual expected
    }

  }

}
