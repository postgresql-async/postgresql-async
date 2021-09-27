package com.github.mauricio.async.db.pool

import org.scalacheck._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import java.util.concurrent.Executors

trait FutureGenInstance {

  implicit def arbitraryFuture[A: Arbitrary](implicit
    ec: ExecutionContext
  ): Arbitrary[() => Future[A]] = {
    Arbitrary(futureGen[A])
  }

  def futureGen[A: Arbitrary](implicit
    ec: ExecutionContext
  ): Gen[() => Future[A]] = {

    val genHangs = Gen.const(() => Promise[A]().future)

    val genFailure =
      Gen.const(() => Future.failed(new Exception(s"Random fail")))

    val genSuccess = Arbitrary.arbitrary[A].map(a => () => Future.successful(a))

    val genDelay = for {
      mills <- Gen.choose(1, 500)
      eOrA <- Gen.either(
        Gen.const(new Exception(s"Random fail")),
        Arbitrary.arbitrary[A]
      )
    } yield () => {
      FutureGenInstance.timer
        .sleep(mills.millis)
        .flatMap(_ => Future.fromTry(eOrA.fold(Failure(_), Success(_))))
    }
    Gen.frequency(
      1 -> genHangs,
      1 -> genFailure,
      1 -> genSuccess,
      4 -> genDelay
    )
  }
}

object FutureGenInstance {
  lazy val timer = FutureTimer(Executors.newScheduledThreadPool(1))
}
