package com.github.mauricio.async.db.pool

import com.github.mauricio.async.db.util._
import java.util.concurrent._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

private[db] trait FutureTimer {

  /**
   * Return a default value if future not completed in specified timeout
   */
  def timeoutTo[A](
    duration: FiniteDuration,
    f: () => Future[A],
    fallback: A
  ): Future[A]

  /**
   * Return a future always complete with success after specified duration
   */
  def sleep(duration: FiniteDuration): Future[Unit]

  /**
   * Fail with [[java.util.conccurent.TimeoutException]] if future not completed
   * in specified timeout
   */
  def timeout[A](
    duration: FiniteDuration,
    f: () => Future[A]
  ): Future[A]
}

private[db] object FutureTimer {

  def apply(ses: ScheduledExecutorService): FutureTimer = {

    new FutureTimer {

      /**
       * Return a future always complete with success after specified duration
       */
      def sleep(duration: FiniteDuration): Future[Unit] = {
        val p = Promise[Unit]()
        ses.schedule(
          new Runnable {
            def run() = {
              p.success(())
            }
          },
          duration.toMillis,
          TimeUnit.MILLISECONDS
        )
        p.future
      }

      private def runWithin[A](
        duration: FiniteDuration,
        f: () => Future[A],
        onTimeout: (Promise[A]) => Unit
      ) = {
        implicit val ec = Execution.naive

        val p = Promise[A]()

        val sf = ses.schedule(
          new Runnable {
            def run() = {
              onTimeout(p)
            }
          },
          duration.toMillis,
          TimeUnit.MILLISECONDS
        )

        f().andThen { case r =>
          if (p.tryComplete(r)) {
            sf.cancel(true)
          }
        }
        p.future
      }

      def timeout[A](
        duration: FiniteDuration,
        f: () => Future[A]
      ): Future[A] = {
        runWithin[A](
          duration,
          f,
          p =>
            p.tryFailure(
              new TimeoutException(s"Future timeout after ${duration}")
            )
        )
      }

      def timeoutTo[A](
        duration: FiniteDuration,
        f: () => Future[A],
        fallback: A
      ): Future[A] = {
        runWithin[A](duration, f, p => p.trySuccess(fallback))
      }
    }
  }
}
