package com.github.mauricio.async.db.pool

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import java.util.concurrent.{CountDownLatch, Semaphore}
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

class MpmcQueueSpec extends Specification with ScalaCheck {

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
    val queue = MpmcQueue[Int](data.capacity)

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
    queue: MpmcQueue[A],
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
    queue: MpmcQueue[A],
    dequeueFunc: MpmcQueue[A] => Option[A],
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

  private case class RunResult(
    enqueueR: Vector[(Int, Boolean)],
    dequeueR: Vector[Int],
    remain: Vector[Int]
  ) {
    def verify() = {
      val remainSize  = remain.size
      val enqueueSize = enqueueR.filter(_._2).size
      val dequeueSize = dequeueR.size

      val itemsMatch = (dequeueR ++ remain).forall { e =>
        enqueueR.exists { case (e1, success) =>
          (e1 == e && success == true)
        }
      }
      itemsMatch && (dequeueSize + remainSize) == enqueueSize
    }
  }

  private def runParallel(
    data: Data,
    dequeueFunc: MpmcQueue[Int] => Option[Int]
  ): RunResult = {
    val queue             = MpmcQueue[Int](data.capacity)
    val elems             = data.input
    val latch             = new CountDownLatch(1)
    val consumeSemaphore  = new Semaphore(data.capacity)
    val producerSemaphore = new Semaphore(data.capacity)

    val f1 = Future.sequence(
      Vector.fill(elems.size * 2)(
        dequeueWithLatch(queue, dequeueFunc, consumeSemaphore, latch)
      )
    )
    val f2 = Future.traverse(elems) { e =>
      enqueueWithLatch(queue, e, producerSemaphore, latch)
    }
    latch.countDown()
    val r1       = Await.result(f1, duration.Duration.Inf)
    val r2       = Await.result(f2, duration.Duration.Inf)
    val dequeueR = r1.collect { case Some(e) => e }
    val remain = Iterator
      .continually(queue.take())
      .takeWhile(_.isDefined)
      .toVector
      .collect { case Some(e) =>
        e
      }
    RunResult(r2, dequeueR, remain)
  }

  val parallelEnqueueDequeue = {
    Prop.forAll { data: Data =>
      val rr = runParallel(data, _.take())
      rr.verify()
    }
  }

  val runParallelWithPeek = {
    Prop.forAll { data: Data =>
      val rr = runParallel(
        data,
        q => {
          q.peek()
          q.take()
        }
      )
      rr.verify()
    }
  }

  val peekLastElem = {
    Prop.forAll { data: Data =>
      val queue = MpmcQueue[Int](data.capacity)
      data.input.map { i =>
        (queue.offer(i), queue.peek() == Some(i), queue.take() == Some(i))
      }.forall { case (r1, r2, r3) =>
        r1 && r2 && r3
      }
    }
  }

  s2"queue must enqueue / dequeue in fifo order ${fifoNoOverflow}"
  s2"enqueue/deuque correctly during parallel access ${parallelEnqueueDequeue}"
  s2"peek should always return last enqueued element ${peekLastElem}"
  s2"peek should not affect enqueue/dequeue during parallel access ${runParallelWithPeek}"
}
