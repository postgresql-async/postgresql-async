package com.github.mauricio.async.db.util

import scala.concurrent._

object Execution {

  object naive extends ExecutionContext {
    override final def execute(runnable: Runnable): Unit = (
      runnable.run()
    )
    override final def reportFailure(t: Throwable): Unit =
      scala.concurrent.ExecutionContext.defaultReporter(t)
  }
}
