package com.github.mauricio.async.db.util

private[util] object ScalaBatchable {

  // see Scala 2.13 source tree for explanation
  def isBatchable(runnable: Runnable): Boolean = runnable match {
    case _: scala.concurrent.OnCompleteRunnable => true
    case _                                      => false
  }

}
