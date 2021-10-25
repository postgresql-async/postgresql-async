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

  case class Config(
    checkInterval: Long,
    createTimeout: Long,
    releaseTimeout: Long,
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
    timer: FutureTimer
  ): SelfHealing[A] = {
    new SelfHealingImpl[A](
      acquire,
      release,
      check,
      config,
      timer
    )
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
      oldItem: Promise[A],
      newItem: Promise[A],
      oldRelease: Promise[Unit]
    ) extends State[A]
  }

  private class SelfHealingImpl[A](
    acquire: () => Future[A],
    release: A => Future[Unit],
    check: A => Future[Boolean],
    config: Config,
    timer: FutureTimer
  ) extends SelfHealing[A] {

    private final val state = new AtomicReference[State[A]](State.Idle)
    private final val logger =
      LoggerFactory.getLogger(classOf[SelfHealingImpl[A]])

    def timeoutAcquire() = {
      timer.timeout(config.createTimeout.millis, () => acquire())
    }

    def timeoutRelease(a: A) = {
      timer.timeoutTo(config.releaseTimeout.millis, () => release(a), ())
    }

    def timeoutCheck(a: A) = {
      timer.timeoutTo(config.checkTimeout.millis, () => check(a), false)
    }

    private def tryHeal(s: State.Ready[A]): Option[Future[A]] = {
      implicit val ec = Execution.naive
      val releaseOld  = Promise[Unit]()
      val acquireNew  = Promise[A]()
      val newState    = State.Swapping(s.promise, acquireNew, releaseOld)
      if (state.compareAndSet(s, newState)) {
        acquireNew.completeWith(timeoutAcquire())
        releaseOld.completeWith(
          s.promise.future.flatMap(timeoutRelease).recover { e =>
            logger.warn(s"Failed to release resource", e)
          }
        )
        releaseOld.future.onComplete { r =>
          val nowMillis = System.currentTimeMillis
          state.set(
            State.Ready(
              lastActive = nowMillis,
              createTime = nowMillis,
              acquireNew
            )
          )
        }
        Some(acquireNew.future)
      } else {
        None
      }
    }

    private def tryHealIfDead(
      curr: State.Ready[A]
    ): Future[A] = {
      implicit val ec = Execution.naive
      val now         = System.currentTimeMillis
      if ((now - curr.lastActive) >= config.checkInterval) {

        def healed(state: State.Ready[A]) = {
          tryHeal(state) match {
            case Some(newRes) => newRes
            case None         => get() // concurrent access detected, retry loop
          }
        }

        val newState = curr.copy(lastActive = now)

        if (state.compareAndSet(curr, newState)) {
          for {
            isAlive <- curr.promise.future
              .flatMap(timeoutCheck)
              .recover(e => false)
            isOk = isAlive || (now - curr.createTime) < config.minHealInterval
            r <- if (isOk) curr.promise.future else healed(newState)
          } yield r
        } else { // under checking, return old resource
          curr.promise.future
        }
      } else {
        curr.promise.future
      }
    }

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
            p.completeWith(timeoutAcquire())
            p.future
          } else get()
        case s @ State.Swapping(
              oldItem,
              newItem,
              oldRelease
            ) =>
          newItem.future
        case s: State.Ready[A] =>
          tryHealIfDead(s)
      }
    }

    def tryRelease(): Future[Boolean] = {
      implicit val ec = Execution.naive
      state.get() match {
        case s @ State.Ready(_, _, p) =>
          if (state.compareAndSet(s, State.Idle)) {
            s.promise.future.flatMap(timeoutRelease).map(_ => true)
          } else Future.successful(false)
        case State.Idle =>
          Future.successful(false)
        case s @ State.Swapping(old, newItem, oldRelease) =>
          if (state.compareAndSet(s, State.Idle)) {
            oldRelease.future
              .flatMap(_ => newItem.future.flatMap(timeoutRelease))
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
