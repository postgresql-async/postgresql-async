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

package com.github.mauricio.async.db.postgresql.messages.frontend

import com.github.mauricio.async.db.column.ColumnEncoderRegistry
import com.github.mauricio.async.db.postgresql.messages.backend.ServerMessage

/**
 * Extended query message that parses an unnamed statement and immediately binds
 * and executes an unnamed portal against it.
 *
 * The unnamed statement is cheaper than a named server-side prepared statement
 * because it does not require an explicit deallocation, so it is used until a
 * query is executed often enough to be promoted to a named statement.
 */
class UnnamedPreparedStatementMessage(
  val query: String,
  val values: Seq[Any],
  encoderRegistry: ColumnEncoderRegistry
) extends ClientMessage(ServerMessage.Parse) {

  val valueTypes: Seq[Int] = values.map { value =>
    encoderRegistry.kindOf(value)
  }

  override def toString(): String =
    s"${this.getClass.getSimpleName}(query=${query},values=${values}})"

}
