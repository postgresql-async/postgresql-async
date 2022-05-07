/*
 * Copyright 2013 Maurício Linhares
 * Copyright 2013 Dylan Simon
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

package com.github.mauricio.async.db.postgresql.column

import com.github.mauricio.async.db.column.ColumnEncoderDecoder
import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException
import com.github.mauricio.async.db.util.Log
import java.time._
import java.time.format._

object PostgreSQLIntervalEncoderDecoder extends ColumnEncoderDecoder {

  private val log = Log.getByName(this.getClass.getName)

  override def encode(value: Any): String = {
    value match {
      case t: Period   => t.toString
      case t: Duration => t.toString // defaults to ISO8601
      case _           => throw new DateEncoderNotAvailableException(value)
    }
  }

  /* This supports all positive intervals, and intervalstyle of postgres_verbose, and iso_8601 perfectly.
   * If intervalstyle is set to postgres or sql_standard, some negative intervals may be rejected.
   */
  def decode(value: String): String = {
    value
  }
}
