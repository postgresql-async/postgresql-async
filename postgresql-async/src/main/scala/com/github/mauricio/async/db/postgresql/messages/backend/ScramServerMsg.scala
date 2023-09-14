package com.github.mauricio.async.db.postgresql.messages.backend

case class AuthSASLReq(
  mechanisms: Array[String]
) extends AuthenticationMessage

case class AuthSASLCont(
  serverFirstMsg: String
) extends AuthenticationMessage

case class AuthSASLFinal(
  serverFinalMsg: String
) extends AuthenticationMessage
