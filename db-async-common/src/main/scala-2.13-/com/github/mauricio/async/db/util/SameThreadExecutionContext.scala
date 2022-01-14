package com.github.mauricio.async.db.util
import scala.concurrent.ExecutionContext

/**
 * Factory to create same thread ec. Not intended to be called from any other
 * site than to create [[akka.dispatch.ExecutionContexts#parasitic]]
 *
 * INTERNAL API
 */
private[util] object SameThreadExecutionContext {

  private val sameThread = new ExecutionContext with BatchingExecutor {
    override protected def unbatchedExecute(runnable: Runnable): Unit =
      runnable.run()
    override protected def resubmitOnBlock: Boolean =
      false // No point since we execute on same thread
    override def reportFailure(t: Throwable): Unit =
      throw new IllegalStateException(
        "exception in sameThreadExecutionContext",
        t
      )
  }

  def apply(): ExecutionContext = sameThread

}
