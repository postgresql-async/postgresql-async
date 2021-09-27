package com.github.mauricio.async.db.pool

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import java.util.concurrent.atomic._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

class SelfHealingSpec
    extends Specification
    with ScalaCheck
    with FutureGenInstance {

  case class Data(
    acquire: () => Future[Int],
    release: () => Future[Unit],
    check: () => Future[Boolean]
  )

  val dataGen = {
    for {
      a <- futureGen[Int]
      r <- futureGen[Unit]
      c <- futureGen[Boolean]
    } yield Data(a, r, c)
  }

  implicit val arbitraryData = Arbitrary(dataGen)

  def runPropTest[A](f: SelfHealing[Int] => Future[A]) = {
    Prop.forAll { data: Data =>
      val createCount     = new AtomicInteger(0)
      val successCreate   = new AtomicInteger(0)
      val releaseCount    = new AtomicInteger(0)
      val successReleased = new AtomicInteger(0)
      val checkCount      = new AtomicInteger(0)
      val successCheck    = new AtomicInteger(0)

      val checkInterval  = 10
      val releaseTimeout = 10
      val checkTimeout   = 10
      val config = SelfHealing.Config(
        checkInterval = 10,
        releaseTimeout = 10,
        checkTimeout = 10,
        minHealInterval = 10
      )

      val acquire = () => {
        createCount.incrementAndGet()
        data.acquire().map { r =>
          successCreate.incrementAndGet()
          r
        }
      }

      val release =
        (i: Int) => {
          releaseCount.incrementAndGet
          data.release().map { _ =>
            successReleased.incrementAndGet()
            ()
          }
        }

      val check = (i: Int) => {
        checkCount.incrementAndGet()
        data.check().map { b =>
          if (b) successCheck.incrementAndGet()
          b
        }
      }

      val sh = SelfHealing(
        acquire,
        release,
        check,
        config,
        FutureGenInstance.timer
      )

      def verifyResult(start: Long, end: Long) = {
        createCount.get <= (checkCount.get() - successCheck.get()) + 1
        successCreate.get() === releaseCount
          .get() // always release success creation
        val checkTime =
          ((end - start) / config.checkInterval).toInt // check at min interval
        checkCount.get() should (be >= (checkTime) and be <= (checkTime + 1))
      }

      val start = System.currentTimeMillis()
      val call = Future
        .traverse(Vector.iterate(0, 10000)(_ + 1)) { i =>
          sh.get()
        }
        .flatMap(_ => sh.tryRelease())
        .map { _ =>
          val end = System.currentTimeMillis
          verifyResult(start, end)
        }

      Await.result(call, Duration.Inf)
    }
  }

}
