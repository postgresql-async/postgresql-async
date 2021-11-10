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

package com.github.mauricio.async.db.postgresql.util

import com.github.mauricio.async.db.Spec
import scala.collection.mutable.ArrayBuffer

class ArrayStreamingParserSpec extends Spec {

  val parser = ArrayStreamingParser

  "parser" - {

    "generate the events correctly" in {

      val content = "{{1,2,3},{4,5,6}}"

      val delegate = new LoggingDelegate()
      parser.parse(content, delegate)

      delegate.starts mustEqual 3
      delegate.ends mustEqual 3
      delegate.items mustEqual ArrayBuffer(
        "{",
        "{",
        "1",
        "2",
        "3",
        "}",
        "{",
        "4",
        "5",
        "6",
        "}",
        "}"
      )
    }

    "should parse a varchar array correctly" in {
      val content =
        """{{"item","is here","but\"not there"},{"so","this is your last step"},{""}}"""

      val delegate = new LoggingDelegate()
      parser.parse(content, delegate)

      delegate.items mustEqual ArrayBuffer(
        "{",
        "{",
        "item",
        "is here",
        "but\"not there",
        "}",
        "{",
        "so",
        "this is your last step",
        "}",
        "{",
        "",
        "}",
        "}"
      )
      delegate.starts mustEqual 4
      delegate.ends mustEqual 4
    }

    "should parse a varchar array with nulls correctly" in {
      val content = """{NULL,"first",NULL,"second","NULL",NULL}"""

      val delegate = new LoggingDelegate()
      parser.parse(content, delegate)

      delegate.items mustEqual ArrayBuffer(
        "{",
        null,
        "first",
        null,
        "second",
        "NULL",
        null,
        "}"
      )
    }

  }

}

class LoggingDelegate extends ArrayStreamingParserDelegate {

  val items  = new ArrayBuffer[String]()
  var starts = 0
  var ends   = 0

  override def arrayStarted = {
    items += "{"
    starts += 1
  }

  override def arrayEnded = {
    items += "}"
    ends += 1
  }

  override def elementFound(element: String) = {
    items += element
  }

  override def nullElementFound = {
    items += null
  }
}
