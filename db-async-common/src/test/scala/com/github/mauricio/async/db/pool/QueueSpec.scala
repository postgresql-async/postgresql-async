package com.github.mauricio.async.db.pool

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import java.util.concurrent.{CountDownLatch, Semaphore}
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

class QueueSpec extends Specification with ScalaCheck {

  case class Data(
    capacity: Int,
    input: Vector[Int]
  )

  val dataGen = for {
    c     <- Gen.choose(1, 10000)
    input <- Arbitrary.arbitrary[Vector[Int]]
  } yield Data(c, input)

  implicit val arbitraryData = Arbitrary(dataGen)

  val fifoNoOverflow = Prop.forAll { data: Data =>
    val queue = Queue[Int](data.capacity)

    val r = data.input
      .grouped(data.capacity)
      .flatMap { batch =>
        val enqueueResult = batch.map(queue.offer).toList
        val dequeueResult = Vector.fill(batch.size)(queue.take())
        enqueueResult.zip(dequeueResult)
      }
      .toList

    val allMatches = r
      .zip(data.input)
      .map { case ((success, dequeue), e) =>
        success && dequeue == Some(e)
      }
      .toList
    r.size == data.input.size && allMatches.forall(_ == true)

  }

  private def enqueueWithLatch[A](
    queue: Queue[A],
    item: A,
    producerSemaphore: Semaphore,
    latch: CountDownLatch
  ) = {
    Future {
      blocking {
        latch.await()
        producerSemaphore.acquire
        val e = item -> queue.offer(item)
        producerSemaphore.release
        e
      }
    }(ExecutionContext.global)
  }

  private def dequeueWithLatch[A](
    queue: Queue[A],
    consumeSemaphore: Semaphore,
    latch: CountDownLatch
  ) = {
    Future {
      blocking {
        latch.await()
        consumeSemaphore.acquire
        val e = queue.take()
        consumeSemaphore.release
        e
      }
    }(ExecutionContext.global)
  }

  val parallelEnqueueDequeue = {
    Prop.forAll { data: Data =>
      val queue             = Queue[Int](data.capacity)
      val elems             = data.input
      val latch             = new CountDownLatch(1)
      val consumeSemaphore  = new Semaphore(data.capacity)
      val producerSemaphore = new Semaphore(data.capacity)

      val f1 = Future.sequence(
        Vector.fill(elems.size * 2)(
          dequeueWithLatch(queue, consumeSemaphore, latch)
        )
      )
      val f2 = Future.traverse(elems) { e =>
        enqueueWithLatch(queue, e, producerSemaphore, latch)
      }
      latch.countDown()
      val r1 = Await.result(f1, duration.Duration.Inf)
      val r2 = Await.result(f2, duration.Duration.Inf)
      val remain = Iterator
        .continually(queue.take())
        .takeWhile(_.isDefined)
        .toVector
        .collect { case Some(e) =>
          e
        }
      val enqueueR    = r2.toMap
      val dequeueR    = r1.collect { case Some(e) => e }
      val remainSize  = remain.size
      val enqueueSize = r2.filter(_._2).size
      val dequeueSize = dequeueR.size

      val itemsMatch = (dequeueR ++ remain).forall { e =>
        val found = r2.exists { case (e1, success) =>
          (e1 == e && success == true)
        }
        if (!found) {
          println(s"${e} not enqueued ${r2.find(_._1 == e)}")
        }
        found
      }
      itemsMatch && (dequeueSize + remainSize) == enqueueSize
    }
  }

  s2"queue must enqueue / dequeue in fifo order ${fifoNoOverflow}"
  s2"enqueue/deuque correctly during parallel access ${parallelEnqueueDequeue}"
}
