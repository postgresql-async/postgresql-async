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

package com.github.mauricio.async.db.mysql.codec

import com.github.mauricio.async.db.ResultSet
import com.github.mauricio.async.db.mysql.message.server._
import io.netty.channel.ChannelHandlerContext

trait MySQLHandlerDelegate {

  def onHandshake(message: HandshakeMessage): Unit
  def onError(message: ErrorMessage): Unit
  def onOk(message: OkMessage): Unit
  def onEOF(message: EOFMessage): Unit
  def exceptionCaught(exception: Throwable): Unit
  def connected(ctx: ChannelHandlerContext): Unit
  def onResultSet(resultSet: ResultSet, message: EOFMessage): Unit
  def switchAuthentication(message: AuthenticationSwitchRequest): Unit

}
