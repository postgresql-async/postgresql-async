/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package com.github.mauricio.async.db.util

import scala.concurrent.ExecutionContext

private[util] object SameThreadExecutionContext {
  def apply(): ExecutionContext = ExecutionContext.parasitic
}
