package com.github.mauricio.async.db.pool

import java.util.concurrent.atomic._
import java.util.concurrent.Semaphore
import scala.concurrent.Future
import scala.concurrent.duration._

private[db] trait Queue[A] {

  /**
   * Enqueue a, return false the queue is full
   */
  def offer(a: A): Boolean

  /**
   * Take element from the queue, return none if queue is empty
   */
  def take(): Option[A]

}

private[db] object Queue {

  def apply[A](capacity: Int): Queue[A] = {
    new ArrayQueueImpl(capacity)
  }

  private class ArrayQueueImpl[A](
    capacity: Int
  ) extends Queue[A] {

    private final val elements = new AtomicReferenceArray(
      Array.ofDim[Option[A]](capacity)
    )
    private final val enqueueSemaphore = new Semaphore(capacity)
    private final val readerIndex      = new AtomicLong(0)
    private final val writerIndex      = new AtomicLong(0)

    private final val maxIdx = Long.MaxValue - Long.MaxValue % capacity - 1

    private def getAndIncrWriteIndex() = {
      val v    = writerIndex.get()
      val next = if (v == maxIdx) 0 else v + 1
    }

    /**
     * Enqueue a, return false the queue is full
     */
    def offer(a: A): Boolean = {
      if (enqueueSemaphore.tryAcquire()) {
        val currIndex =
          writerIndex.getAndUpdate(e => if (e == maxIdx) 0 else e + 1)
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
            ) // producer sometimes have not finished set element to the idx
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
