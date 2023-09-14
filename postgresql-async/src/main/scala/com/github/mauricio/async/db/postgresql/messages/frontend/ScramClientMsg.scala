package com.github.mauricio.async.db.postgresql.messages.frontend

import com.github.mauricio.async.db.postgresql.messages.backend.ServerMessage

private[postgresql] sealed abstract class ScramAuthMsg
    extends ClientMessage(ServerMessage.PasswordMessage)

private[postgresql] case class ScramAuthFirstMsg(
  mechanismName: String,
  firstMsg: String
) extends ScramAuthMsg

private[postgresql] case class ScramAuthFinalMsg(
  finalMsg: String
) extends ScramAuthMsg
