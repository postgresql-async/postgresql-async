package com.github.mauricio.async.db.util

import java.util.ArrayDeque
import java.util.concurrent.Executor
import scala.annotation.tailrec
import scala.concurrent._

/**
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com> Copied
 * from akka
 */
private[util] trait BatchingExecutor extends Executor {

  // invariant: if "_tasksLocal.get ne null" then we are inside Batch.run; if it is null, we are outside
  private[this] val _tasksLocal = new ThreadLocal[AbstractBatch]()

  private[this] abstract class AbstractBatch
      extends ArrayDeque[Runnable](4)
      with Runnable {
    @tailrec final def processBatch(batch: AbstractBatch): Unit =
      if ((batch eq this) && !batch.isEmpty) {
        batch.poll().run()
        processBatch(
          _tasksLocal.get
        ) // If this is null, then we have been using managed blocking, so bail out
      }

    protected final def resubmitUnbatched(): Boolean = {
      val current = _tasksLocal.get()
      _tasksLocal.remove()
      if ((current eq this) && !current.isEmpty) { // Resubmit ourselves if something bad happened and we still have work to do
        unbatchedExecute(current) // TODO what if this submission fails?
        true
      } else false
    }
  }

  private[this] final class Batch extends AbstractBatch {
    override final def run: Unit = {
      require(_tasksLocal.get eq null)
      _tasksLocal.set(this) // Install ourselves as the current batch
      try processBatch(this)
      catch {
        case t: Throwable =>
          resubmitUnbatched()
          throw t
      } finally _tasksLocal.remove()
    }
  }

  private[this] val _blockContext = new ThreadLocal[BlockContext]()

  private[this] final class BlockableBatch
      extends AbstractBatch
      with BlockContext {
    // this method runs in the delegate ExecutionContext's thread
    override final def run(): Unit = {
      require(_tasksLocal.get eq null)
      _tasksLocal.set(this) // Install ourselves as the current batch
      val firstInvocation = _blockContext.get eq null
      if (firstInvocation) _blockContext.set(BlockContext.current)
      BlockContext.withBlockContext(this) {
        try processBatch(this)
        catch {
          case t: Throwable =>
            resubmitUnbatched()
            throw t
        } finally {
          _tasksLocal.remove()
          if (firstInvocation) _blockContext.remove()
        }
      }
    }

    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      // if we know there will be blocking, we don't want to keep tasks queued up because it could deadlock.
      resubmitUnbatched()
      // now delegate the blocking to the previous BC
      _blockContext.get.blockOn(thunk)
    }
  }

  protected def unbatchedExecute(r: Runnable): Unit

  protected def resubmitOnBlock: Boolean

  override def execute(runnable: Runnable): Unit = {
    if (batchable(runnable)) { // If we can batch the runnable
      _tasksLocal.get match {
        case null =>
          val newBatch: AbstractBatch =
            if (resubmitOnBlock) new BlockableBatch() else new Batch()
          newBatch.add(runnable)
          unbatchedExecute(
            newBatch
          ) // If we aren't in batching mode yet, enqueue batch
        case batch =>
          batch.add(
            runnable
          ) // If we are already in batching mode, add to batch
      }
    } else
      unbatchedExecute(
        runnable
      ) // If not batchable, just delegate to underlying
  }

  /** Override this to define which runnables will be batched. */
  def batchable(runnable: Runnable): Boolean =
    ScalaBatchable.isBatchable(runnable)
}
