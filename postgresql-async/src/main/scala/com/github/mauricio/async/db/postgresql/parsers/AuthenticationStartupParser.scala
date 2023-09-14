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

import com.github.mauricio.async.db.exceptions.UnsupportedAuthenticationMethodException
import com.github.mauricio.async.db.postgresql.messages.backend._
import io.netty.buffer.ByteBuf
import scala.collection.mutable.ArrayBuilder

object AuthenticationStartupParser extends MessageParser {

  val AuthenticationOk                = 0
  val AuthenticationKerberosV5        = 2
  val AuthenticationCleartextPassword = 3
  val AuthenticationMD5Password       = 5
  val AuthenticationSCMCredential     = 6
  val AuthenticationGSS               = 7
  val AuthenticationGSSContinue       = 8
  val AuthenticationSSPI              = 9
  val AuthenticationSASL              = 10
  val AuthenticationSASLCont          = 11
  val AuthenticationSASLFin           = 12

  override def parseMessage(b: ByteBuf): ServerMessage = {

    val authenticationType = b.readInt()

    authenticationType match {
      case AuthenticationOk => AuthenticationOkMessage.Instance
      case AuthenticationCleartextPassword =>
        AuthenticationChallengeCleartextMessage.Instance
      case AuthenticationMD5Password =>
        val bytes = new Array[Byte](b.readableBytes())
        b.readBytes(bytes)
        new AuthenticationChallengeMD5(bytes)
      case AuthenticationSASL =>
        val sbb = ArrayBuilder.make[String]
        while (b.readableBytes > 0) {
          sbb += readCString(b)
        }
        AuthSASLReq(sbb.result())
      case AuthenticationSASLCont =>
        val ba = Array.ofDim[Byte](b.readableBytes)
        b.readBytes(ba)
        val serverFirst = new String(ba, "UTF-8")
        AuthSASLCont(serverFirst)
      case AuthenticationSASLFin =>
        val ba = Array.ofDim[Byte](b.readableBytes)
        b.readBytes(ba)
        val finalMsg = new String(ba, "UTF-8")
        AuthSASLFinal(finalMsg)
      case _ => {
        throw new UnsupportedAuthenticationMethodException(authenticationType)
      }
    }
  }

  private def readCString(buf: ByteBuf) = {
    val bb = ArrayBuilder.make[Byte]
    var ch = buf.readByte()
    while (ch != 0) {
      bb += ch
      if (buf.readableBytes > 0) {
        ch = buf.readByte()
      }
    }
    new String(bb.result(), "UTF-8")
  }

}
