package com.github.mauricio.async.db.pool

import scala.concurrent.{Future, Promise}

/**
 * A self-healing lazy, async inited resource (connection, for example)
 */
private[db] trait SelfHealing[A] {
  def get(): Future[A]
}

object SelfHealing {

  /**
   * Create swappable resource
   */
  def apply[A](
    acquire: () => Future[A],
    release: A => Future[Unit],
    check: A => Future[Boolean]
  ): SelfHealing[A] = {
    ???
  }

  private sealed trait State[+A]
  private case object Idle                                extends State[Nothing]
  private case class Initializing[A](promise: Promise[A]) extends State[A]
  private case class Ready[A](item: A)                    extends State[A]
  private case class Healing[A](dead: A, replacer: Promise[A]) extends State[A]
}
