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
  def peek(): Option[A]

}

private[db] object MpmcQueue {

  def apply[A](capacity: Int): MpmcQueue[A] = {
    apply(capacity, Long.MaxValue)
  }

  private[db] def apply[A](
    capacity: Int,
    indexUpperBound: Long
  ): MpmcQueue[A] = {
    new ArrayQueueImpl(capacity, indexUpperBound)
  }

  private class ArrayQueueImpl[A](
    capacity: Int,
    indexUpperBound: Long
  ) extends MpmcQueue[A] {

    // ensure reader index != writerIndex if queue is full
    private final val arraySize = capacity + 1
    private final val elements = new AtomicReferenceArray(
      Array.ofDim[Option[A]](capacity + 1)
    )
    private final val enqueueSemaphore = new Semaphore(capacity)
    private final val readerIndex      = new AtomicLong(0)
    private final val writerIndex      = new AtomicLong(0)
    private final val maxIdx = indexUpperBound - indexUpperBound % arraySize - 1

    @annotation.tailrec
    private def getAndIncrWriteIndex(): Long = {
      val curr = writerIndex.get()
      val next = if (curr == maxIdx) 0 else curr + 1
      if (writerIndex.compareAndSet(curr, next)) {
        curr
      } else getAndIncrWriteIndex()
    }

    @inline
    private def arrayOffsetOfIdx(index: Long) = {
      (index % arraySize).toInt
    }

    /**
     * Enqueue a, return false the queue is full
     */
    def offer(a: A): Boolean = {
      val before = enqueueSemaphore.availablePermits()
      if (enqueueSemaphore.tryAcquire()) {
        val currIndex = getAndIncrWriteIndex()
        elements.set(arrayOffsetOfIdx(currIndex), Some(a))
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

    def peek(): Option[A] = {
      def loop(): Option[A] = {
        val currReader = readerIndex.get()
        val currWriter = writerIndex.get()
        if (currReader != currWriter) {
          val idx  = arrayOffsetOfIdx(currReader)
          val elem = elements.get(idx)
          if (elem == null || elem == None) { // elemement might already consumed or not inited, try again
            loop()
          } else elem
        } else {
          None
        }
      }
      loop()
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
            val e = readUntilDefined(arrayOffsetOfIdx(currReader))
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
