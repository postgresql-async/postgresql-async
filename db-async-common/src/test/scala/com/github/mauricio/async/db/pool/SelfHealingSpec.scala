package com.github.mauricio.async.db.pool

import java.util.concurrent.atomic._
import com.github.mauricio.async.db.Spec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class SelfHealingSpec
    extends Spec
    with ScalaCheckPropertyChecks
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
    createTimeout = 10
  )

  val dataGen = {
    for {
      a <- futureGen[Int]
      r <- futureGen[Unit]
      c <- futureGen[Boolean]
    } yield Data(a, r, c)
  }

  implicit val arbitraryData: Arbitrary[Data] = Arbitrary(dataGen)

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

  "SelfHealing item" - {
    "should always release created resource" in {
      forAll { (data: Data, i: Int) =>
        val r = runPropTest(data) { sh =>
          def act() = {
            FutureGenInstance.timer
              .sleep(
                (math.abs(i) % (config.checkInterval * 2)).millis
              )
              .flatMap { _ =>
                sh.get()
              }
          }
          Future.sequence(Seq.fill(1000)(act())).recover { case e =>
            println(s"Failed get resource ${e}")
          }
        }
        r.successCreate mustEqual r.releaseCount
      }
    }

    "should recreate resoure if check return false" in {
      forAll { (data: Data) =>
        val nd = data.copy(
          check = () => Future.successful(false),
          acquire = () => Future(System.currentTimeMillis().toInt)
        )
        val rr = runPropTest(nd) { sh =>
          for {
            _ <- sh.get().recover { case e: Throwable =>
            }
            _ <- FutureGenInstance.timer.sleep(
              (config.checkInterval + 10).millis
            )
            _ <- sh.get().recover { case e: Throwable => }
          } yield {}
        }
        rr.checkCount mustEqual (1)
        rr.createCount mustEqual (2)
      }
    }

    "should recreate resoure if check failure" in {
      forAll { (data: Data) =>
        val nd = data.copy(
          check = () => Future.failed(new Exception(s"Check failed")),
          acquire = () => Future(System.currentTimeMillis().toInt)
        )
        val rr = runPropTest(nd) { sh =>
          for {
            _ <- sh.get().recover { case e: Throwable =>
            }
            start = System.currentTimeMillis()
            _ <- FutureGenInstance.timer.sleep(
              (config.checkInterval + 10).millis
            )
            end = System.currentTimeMillis
            _ <- sh.get().recover { case e: Throwable =>
            }
          } yield {}
        }
        rr.checkCount mustEqual (1)
        rr.createCount mustEqual (2)
      }
    }

    "should create only once with in min-interval" in {
      forAll { (data: Data) =>
        val nd = data.copy(
          acquire = () => Future(System.currentTimeMillis().toInt),
          check = () => Future.successful(false)
        )
        val rr = runPropTest(nd) { sh =>
          for {
            _ <- sh.get().recover { case e: Throwable => }
            _ <- FutureGenInstance.timer.sleep(
              (config.checkInterval + 1).millis
            )
            _ <- sh.get().recover { case e: Throwable => }
            _ <- sh.get().recover { case e: Throwable => }
            _ <- sh.get().recover { case e: Throwable => }
          } yield {}
        }
        rr.checkCount mustEqual (1)
        rr.createCount mustEqual (2)
      }
    }
  }

}
