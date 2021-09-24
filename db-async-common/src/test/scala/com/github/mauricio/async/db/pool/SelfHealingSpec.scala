package com.github.mauricio.async.db.pool

import org.specs2.mutable.Specification
import org.specs2.ScalaCheck
import org.scalacheck._
import scala.concurrent._

class SelfHealingSpec extends Specification with ScalaCheck {

  def futureGen[A: Arbitrary] = {
    val genDead = Gen.const(Promise[A]().future)
    val genPure = Arbitrary.arbitrary[A].map(Future.successful)
  }

  Prop.forAll(Gen.choose(10, 10000)) { times =>
    ???
  }
}
