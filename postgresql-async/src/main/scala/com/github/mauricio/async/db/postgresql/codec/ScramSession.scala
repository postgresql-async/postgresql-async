package com.github.mauricio.async.db.postgresql.codec

import com.ongres.scram.client._
import com.ongres.scram.common.stringprep.StringPreparations

private[postgresql] trait ScramAuthSession {
  def clientFirstMsg(): (String, String)
  def clientFinalMsg(serverFirstMsg: String): String
  def verifyServerFinalMsg(serverFinalMsg: String): Unit
}

private[postgresql] object ScramAuthSession {

  def apply(password: String, mechanisms: Array[String]) =
    new ScramAuthSession {
      private val _scramClient = ScramClient
        .channelBinding(ScramClient.ChannelBinding.NO)
        .stringPreparation(StringPreparations.SASL_PREPARATION)
        .selectMechanismBasedOnServerAdvertised(mechanisms: _*)
        .setup()

      private val _session = _scramClient.scramSession("*")
      private var clientFinalProcessor: ScramSession#ClientFinalProcessor = null

      def scramClient = _scramClient

      def clientFirstMsg() = {
        val fm = _session.clientFirstMessage()
        val mn = _scramClient.getScramMechanism().getName()
        (mn, fm)
      }

      def clientFinalMsg(serverFirstMsg: String) = {
        val serverFirstProcessor =
          _session.receiveServerFirstMessage(serverFirstMsg)
        clientFinalProcessor =
          serverFirstProcessor.clientFinalProcessor(password)
        clientFinalProcessor.clientFinalMessage()
      }

      def verifyServerFinalMsg(serverFinalMsg: String) = {
        clientFinalProcessor.receiveServerFinalMessage(serverFinalMsg)
      }

    }
}
