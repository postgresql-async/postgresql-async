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

  final val config = SelfHealing.Config(
    checkInterval = 10,
    releaseTimeout = 10,
    checkTimeout = 10,
    minHealInterval = 10,
    createTimeout = 10
  )

  val dataGen = {
    for {
      a <- futureGen[Int]
      r <- futureGen[Unit]
      c <- futureGen[Boolean]
    } yield Data(a, r, c)
  }

  implicit val arbitraryData = Arbitrary(dataGen)

  case class RunResult(
    createCount: Int,
    successCreate: Int,
    releaseCount: Int,
    successReleased: Int,
    checkCount: Int,
    successCheck: Int,
    startAt: Long,
    endAt: Long
  )

  private def runPropTest[A](
    data: Data
  )(func: SelfHealing[Int] => Future[A]) = {
    val createCount     = new AtomicInteger(0)
    val successCreate   = new AtomicInteger(0)
    val releaseCount    = new AtomicInteger(0)
    val successReleased = new AtomicInteger(0)
    val checkCount      = new AtomicInteger(0)
    val successCheck    = new AtomicInteger(0)

    val checkInterval  = 10
    val releaseTimeout = 10
    val checkTimeout   = 10

    val acquire = () => {
      val c = createCount.incrementAndGet()
      println(s"Create timets: ${c}")
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
    val call = func(sh)
      .flatMap(_ =>
        sh.tryRelease().recover { case e =>
          false
        }
      )
      .map { _ =>
        val end = System.currentTimeMillis
        RunResult(
          createCount = createCount.get(),
          successCreate = successCreate.get(),
          releaseCount = releaseCount.get(),
          successReleased = successReleased.get(),
          checkCount = checkCount.get(),
          successCheck = successCheck.get(),
          startAt = start,
          endAt = end
        )
      }

    Await.result(call, Duration.Inf)
  }

  val alwaysReleasedResources = Prop.forAll { data: Data =>
    val r = runPropTest(data) { sh =>
      Future.sequence(Seq.fill(1000)(sh.get())).recover { case e =>
        println(s"Failed get resource ${e}")
      }
    }
    val maxGap = (r.endAt - r.startAt) / 10 + 1
    //println(s"successCreate: ${r.successCreate} , createCount: ${r.createCount} , release: ${r.releaseCount} , checkCount: ${r.checkCount} , maxGap: ${maxGap}")
    r.successCreate === r.releaseCount
    r.checkCount <= maxGap
    r.createCount <= maxGap
    r.createCount <= (r.checkCount - r.successCheck) + 1
  }

  val recreateIfDead = Prop.forAll { data: Data =>
    val nd = data.copy(check = () => Future.successful(false))
    val rr = runPropTest(nd) { sh =>
      for {
        _ <- sh.get().recover(e => {})
        start = System.currentTimeMillis()
        _ <- FutureGenInstance.timer.sleep((config.checkInterval + 10).millis)
        end = System.currentTimeMillis
        _   = println(s"${end - start}.......")
        _ <- sh.get().recover(e => {})
      } yield {}
    }
    rr.checkCount shouldEqual (1)
    rr.createCount shouldEqual (2)
  }

  s2"Always release created resource ${alwaysReleasedResources}"
  s2"Recreate if resource is dead ${recreateIfDead}"
}
