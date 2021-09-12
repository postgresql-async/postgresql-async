package com.mauricio.async.db.pool

import java.util.concurrent.atomic._
import java.util.concurrent.Semaphore
import scala.concurrent.Future
import scala.concurrent.duration._

private[db] trait Queue[A <: AnyRef] {

  /**
   * Enqueue a, return false the queue is full
   */
  def offer(a: A): Boolean

  /**
   * Take element from the queue, return none if queue is empty
   */
  def take(): Option[A]

  /**
   * Try take element, failed with TimeoutException if no elements avaliable at
   * specified timeout
   */
  def takeIn(a: A, timeout: FiniteDuration): Future[A]
}

private[db] object Queue {

  private class ArrayQueueImpl[A <: AnyRef: scala.reflect.ClassTag](
    capacity: Int
  ) extends Queue[A] {

    private final val elements = new AtomicReferenceArray(
      Array.ofDim[A](capacity)
    )
    private final val enqueueSemaphore = new Semaphore(capacity)
    private final val readerIndex      = new AtomicLong(0)
    private final val writerIndex      = new AtomicLong(0)

    /**
     * Enqueue a, return false the queue is full
     */
    def offer(a: A): Boolean = {
      if (enqueueSemaphore.tryAcquire()) {
        val nextIdx = (writerIndex.incrementAndGet() % capacity).toInt
        elements.set(nextIdx - 1, a)
        true
      } else false
    }

    /**
     * Take element from the queue, return none if queue is empty
     */
    def take(): Option[A] = {
      def loop(): Option[A] = {
        val currReader = readerIndex.get()
        val currWriter = writerIndex.get()
        if (currReader != currWriter) {
          val nextReader = ((currReader + 1) % capacity).toInt

          if (readerIndex.compareAndSet(currReader, nextReader)) {
            Some(elements.get(currReader.toInt))
          } else {
            loop()
          }
        } else {
          None
        }
      }
      loop()
    }

    /**
     * Try take element, failed with TimeoutException if no elements avaliable
     * at specified timeout
     */
    def takeIn(a: A, timeout: FiniteDuration): Future[A] = {
      ???
    }
  }
}
