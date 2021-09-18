package com.github.mauricio.async.db.pool

import com.github.mauricio.async.db.util.Execution
import java.util.concurrent.atomic._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
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
   * Release the resource now if it is not used. If [[get]] is called later,
   * resource will be recreate
   */
  def tryRelease(): Future[Boolean]

  /**
   * Mark resource is active right now.
   */
  def trySetActive(): Boolean
}

object SelfHealing {

  object Timer {
    type CancelToken = () => Unit
  }

  trait Timer {
    def setTimeout(duration: FiniteDuration, f: () => Unit): Timer.CancelToken
  }

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
    config: Config,
    timer: Timer
  ): SelfHealing[A] = {
    ???
  }

  private sealed trait State[+A]
  private object State {
    case object Idle extends State[Nothing]

    case class Ready[A](
      lastActive: Long,
      createTime: Long,
      promise: Promise[A]
    ) extends State[A]

    case class Swapping[A](
      oldItem: A,
      newItem: Promise[A],
      oldRelease: Promise[Unit]
    ) extends State[A]
  }

  private class SelfHealingImpl[A](
    acquire: () => Future[A],
    release: A => Future[Unit],
    check: A => Future[Boolean],
    config: Config
  ) extends SelfHealing[A] {

    val state = new AtomicReference[State[A]](State.Idle)

    def get(): Future[A] = {
      state.get() match {
        case State.Idle =>
          val p         = Promise[A]()
          val nowMillis = System.currentTimeMillis()
          if (
            state.compareAndSet(
              State.Idle,
              State.Ready(nowMillis, nowMillis, p)
            )
          ) {
            p.completeWith(acquire())
            p.future
          } else get()
        case s @ State.Swapping(
              oldItem,
              newItem,
              oldRelease
            ) =>
          implicit val ec = Execution.naive
          oldRelease.future.flatMap(_ => newItem.future)
        case i @ State.Ready(c, l, p) =>
          p.future
      }
    }

    def tryRelease(): Future[Boolean] = {
      implicit val ec = Execution.naive
      state.get() match {
        case s @ State.Ready(_, _, p) =>
          if (state.compareAndSet(s, State.Idle)) {
            s.promise.future.flatMap(release).map(_ => true)
          } else Future.successful(false)
        case State.Idle =>
          Future.successful(false)
        case s @ State.Swapping(old, newItem, oldRelease) =>
          if (state.compareAndSet(s, State.Idle)) {
            oldRelease.future
              .flatMap(_ => newItem.future.flatMap(release))
              .map(_ => true)
          } else Future.successful(false)
      }
    }

    def trySetActive() = {
      state.get() match {
        case State.Idle => false
        case r @ State.Ready(t, a, p) =>
          val now = System.currentTimeMillis
          state.compareAndSet(r, State.Ready(t, a, p))
        case _: State.Swapping[A] =>
          false
      }
    }
  }

}
