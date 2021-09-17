package com.github.mauricio.async.db.pool

import java.util.concurrent.atomic._
import scala.concurrent.{Future, Promise}
import org.slf4j.LoggerFactory

/**
 * A self-healing lazy, async inited resource (connection, for example)
 */
private[db] trait SelfHealing[A] {

  /**
   * Get the resource instance, recreate if dead
   */
  def get(): Future[A]

  /**
   * Release the resource now. If [[get]] is called later, resource will be
   * recreate
   */
  def releaseNow(): Future[A]
}

object SelfHealing {

  case class Config(
    checkInterval: Long,
    checkTimeout: Long,
    minHealInterval: Long
  )

  /**
   * Create swappable resource
   */
  def apply[A](
    acquire: () => Future[A],
    release: A => Future[Unit],
    check: A => Future[Boolean],
    config: Config
  ): SelfHealing[A] = {
    ???
  }

  private sealed trait State[+A]

  private case object Idle extends State[Nothing]

  private case class Inited[A](createTime: Long, promise: Promise[A])
      extends State[A]

  private class SelfHealingImpl[A](
    acquire: () => Future[A],
    release: A => Future[Unit],
    check: A => Future[Boolean],
    config: Config
  ) extends SelfHealing[A] {

    val state = new AtomicReference[State[A]](Idle)

    def get(): Future[A] = {
      state.get() match {
        case Idle =>
          val p = Promise[A]()
          if (
            state.compareAndSet(Idle, Inited(System.currentTimeMillis(), p))
          ) {
            p.completeWith(acquire())
            p.future
          } else get()
        case i @ Inited(t, p) =>
          p.future
      }
    }

    def releaseNow(): Future[A] = {
      ???
    }
  }

}
