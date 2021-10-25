package com.github.mauricio.async.db.pool

import com.github.mauricio.async.db.pool.AbstractAsyncObjectPoolSpec.Widget
import com.github.mauricio.async.db.Spec

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

import scala.concurrent.{Await, Future}
import scala.util.Failure

import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * This spec is designed abstract to allow testing of any implementation of
 * AsyncObjectPool, against the common requirements the interface expects.
 *
 * @tparam T
 *   the AsyncObjectPool being tested.
 */
abstract class AbstractAsyncObjectPoolSpec[T <: AsyncObjectPool[Widget]](
  label: String
) extends Spec {

  import AbstractAsyncObjectPoolSpec._

  protected def pool(
    factory: ObjectFactory[Widget] = new TestWidgetFactory,
    conf: PoolConfiguration = PoolConfiguration.Default
  ): T

  // Evaluates to the type of AsyncObjectPool
  s"the ${label} variant of AsyncObjectPool" - {

    "successfully retrieve and return a Widget" in {
      val p      = pool()
      val widget = Await.result(p.take, Duration.Inf)

      widget must not be (null)

      val thePool = Await.result(p.giveBack(widget), Duration.Inf)
      thePool must be(p)
    }

    "reject Widgets that did not come from it" in {
      val p = pool()
      assertThrows[IllegalArgumentException] {
        Await.result(p.giveBack(Widget(null)), Duration.Inf)
      }
    }

    "scale contents" - {

      val factory = spy(new TestWidgetFactory)

      val p = pool(
        factory = factory,
        conf = PoolConfiguration(
          maxObjects = 5,
          maxIdle = 2,
          maxQueueSize = 5,
          validationInterval = 2000
        )
      )

      var taken = Seq.empty[Widget]
      "can take up to maxObjects" in {
        taken = Await.result(
          Future.sequence(for (i <- 1 to 5) yield p.take),
          Duration.Inf
        )

        taken must have size 5
        taken.head must not be null
        taken(1) must not be null
        taken(2) must not be null
        taken(3) must not be null
        taken(4) must not be null
      }

      "does not attempt to expire taken items" in {
        // Wait 3 seconds to ensure idle check has run at least once
        verify(factory, timeout(3000).times(0)).destroy(any[Widget])
      }

      reset(
        factory
      ) // Considered bad form, but necessary as we depend on previous state in these tests
      "takes maxObjects back" in {
        val returns = Await.result(
          Future.sequence(for (widget <- taken) yield p.giveBack(widget)),
          Duration.Inf
        )

        returns must have size 5

        returns.head must be(p)
        returns(1) must be(p)
        returns(2) must be(p)
        returns(3) must be(p)
        returns(4) must be(p)
      }

      "protest returning an item that was already returned" in {
        val resultFuture = p.giveBack(taken.head)
        assertThrows[IllegalStateException] {
          Await.result(resultFuture, Duration.Inf)
        }
      }

      "destroy down to maxIdle widgets" in {
        Thread.sleep(3000)
        verify(factory, times(5)).destroy(any[Widget])
      }
    }

    "queue requests after running out" in {
      val p = pool(
        conf = PoolConfiguration.Default.copy(maxObjects = 2, maxQueueSize = 1)
      )

      val widgets = Await.result(
        Future.sequence(for (i <- 1 to 2) yield p.take),
        Duration.Inf
      )

      val future = p.take

      // Wait five seconds
      Thread.sleep(5000)

      val failedFuture = p.take

      // Cannot be done, would exceed maxObjects
      future.isCompleted mustEqual (false)

      assertThrows[PoolExhaustedException] {
        Await.result(failedFuture, Duration.Inf)
      }
      Await.result(p.giveBack(widgets.head), Duration.Inf) must be(p)
      Await.result(future, Duration(5, SECONDS)) must be(widgets.head)
    }

    "refuse to allow take after being closed" in {
      val p = pool()

      Await.result(p.close, Duration.Inf) must be(p)

      an[PoolAlreadyTerminatedException] must be thrownBy {
        Await.result(p.take, Duration.Inf)
      }
    }

    "allow being closed more than once" in {
      val p = pool()

      Await.result(p.close, Duration.Inf) must be(p)

      Await.result(p.close, Duration.Inf) must be(p)
    }

    "destroy a failed widget" in {
      val factory = spy(new TestWidgetFactory)
      val p       = pool(factory = factory)

      val widget = Await.result(p.take, Duration.Inf)

      widget must not be null

      when(factory.validate(widget)).thenReturn {
        Failure(
          new RuntimeException("This is a bad widget!")
        )
      }

      the[RuntimeException] thrownBy Await.result(
        p.giveBack(widget),
        Duration.Inf
      ) must have message "This is a bad widget!"
      verify(factory, atLeastOnce()).destroy(widget)
    }

    "clean up widgets that die in the pool" in {
      val factory = spy(new TestWidgetFactory)
      // Deliberately make it impossible to expire (nearly)
      val p = pool(
        factory = factory,
        conf = PoolConfiguration.Default
          .copy(maxIdle = Long.MaxValue, validationInterval = 2000)
      )

      val widget = Await.result(p.take, Duration.Inf)

      widget must not be null

      Await.result(p.giveBack(widget), Duration.Inf) must be(p)

      verify(factory, atLeastOnce()).validate(widget)
      verify(factory, never()).destroy(widget)
      verify(factory, timeout(3000).atLeast(2)).validate(widget)
      when(factory.validate(widget)).thenReturn {
        Failure(new RuntimeException("Test Exception, Not an Error"))
      }

      verify(factory, timeout(3000).times(1)).destroy(widget)
      Await.ready(p.take, Duration.Inf)
      verify(factory, times(2)).create
    }

  }

}

object AbstractAsyncObjectPoolSpec {

  case class Widget(factory: TestWidgetFactory)

  class TestWidgetFactory extends ObjectFactory[Widget] {

    override def create: Widget = Widget(this)

    override def destroy(item: Widget) = {}

    override def validate(item: Widget): Try[Widget] = Try {
      if (item.factory eq this)
        item
      else
        throw new IllegalArgumentException("Not our item")
    }
  }

}

class SingleThreadedAsyncObjectPoolSpec
    extends AbstractAsyncObjectPoolSpec[SingleThreadedAsyncObjectPool[Widget]](
      "SingleThread"
    ) {

  import AbstractAsyncObjectPoolSpec._

  override protected def pool(
    factory: ObjectFactory[Widget],
    conf: PoolConfiguration
  ) =
    new SingleThreadedAsyncObjectPool(factory, conf)

  "SingleThreadedAsyncObjectPool" - {
    "successfully record a closed state" in {
      val p = pool()

      Await.result(p.close, Duration.Inf) mustEqual (p)

      p.isClosed must be(true)
    }

  }

}
