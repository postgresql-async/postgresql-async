package com.github.mauricio.async.db.pool

import java.util.concurrent.atomic._
import java.util.concurrent.Semaphore
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * A simple multi-producer-multi-consumer queue used internally
 */
private[db] trait MpmcQueue[A] {

  /**
   * Enqueue a, return false the queue is full
   */
  def offer(a: A): Boolean

  /**
   * Take element from the queue, return none if queue is empty
   */
  def take(): Option[A]

  /**
   * Take element only if the element matches [[pred]]
   */
  def takeIf(pred: A => Boolean): Option[A]

}

private[db] object MpmcQueue {

  def apply[A](capacity: Int): MpmcQueue[A] = {
    new ArrayQueueImpl(capacity)
  }

  private class ArrayQueueImpl[A](
    capacity: Int
  ) extends MpmcQueue[A] {

    private final val elements = new AtomicReferenceArray(
      Array.ofDim[Option[A]](capacity)
    )
    private final val enqueueSemaphore = new Semaphore(capacity)
    private final val readerIndex      = new AtomicLong(0)
    private final val writerIndex      = new AtomicLong(0)

    private final val maxIdx = Long.MaxValue - Long.MaxValue % capacity - 1

    @annotation.tailrec
    private def getAndIncrWriteIndex(): Long = {
      val curr = writerIndex.get()
      val next = if (curr == maxIdx) 0 else curr + 1
      if (writerIndex.compareAndSet(curr, next)) {
        curr
      } else getAndIncrWriteIndex()
    }

    /**
     * Enqueue a, return false the queue is full
     */
    def offer(a: A): Boolean = {
      if (enqueueSemaphore.tryAcquire()) {
        val currIndex = getAndIncrWriteIndex()
        elements.set((currIndex % capacity).toInt, Some(a))
        true
      } else false
    }

    @annotation.tailrec
    private def readUntilDefined(idx: Int): Option[A] = {
      val r = elements.getAndSet(idx, None)
      if (r != null && r != None) {
        r
      } else readUntilDefined(idx)
    }

    def takeIf(pred: A => Boolean): Option[A] = {
      ???
    }

    /**
     * Take element from the queue, return none if queue is empty
     */
    def take(): Option[A] = {
      @annotation.tailrec
      def loop(): Option[A] = {
        val currReader = readerIndex.get()
        val currWriter = writerIndex.get()
        if (currReader != currWriter) {
          val nextReader = if (currReader == maxIdx) 0 else currReader + 1
          if (readerIndex.compareAndSet(currReader, nextReader)) {
            val idx = (currReader % capacity).toInt
            val e = readUntilDefined(
              idx
            ) // ensure element has be written
            enqueueSemaphore.release()
            e
          } else {
            loop()
          }
        } else {
          None
        }
      }
      loop()
    }
  }
}
