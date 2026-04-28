package com.github.mauricio.async.db.postgresql.codec

import com.ongres.scram.client._
import java.util.Arrays

private[postgresql] trait ScramAuthSession {
  def clientFirstMsg(): (String, String)
  def clientFinalMsg(serverFirstMsg: String): String
  def verifyServerFinalMsg(serverFinalMsg: String): Unit
}

private[postgresql] object ScramAuthSession {

  def apply(password: String, mechanisms: Array[String]): ScramAuthSession =
    new ScramAuthSession {
      private val _scramClient = ScramClient
        .builder()
        .advertisedMechanisms(Arrays.asList(mechanisms: _*))
        .username("*")
        .password(password.toCharArray)
        .build()

      def scramClient = _scramClient

      def clientFirstMsg() = {
        val fm = _scramClient.clientFirstMessage()
        val mn = _scramClient.getScramMechanism().getName()
        (mn, fm.toString)
      }

      def clientFinalMsg(serverFirstMsg: String) = {
        _scramClient.serverFirstMessage(serverFirstMsg)
        _scramClient.clientFinalMessage().toString
      }

      def verifyServerFinalMsg(serverFinalMsg: String) = {
        _scramClient.serverFinalMessage(serverFinalMsg)
      }

    }
}
