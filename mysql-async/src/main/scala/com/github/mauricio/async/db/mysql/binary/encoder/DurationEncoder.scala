/*
 * Copyright 2013 Maurício Linhares
 *
 * Maurício Linhares licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.mauricio.async.db.mysql.binary.encoder

import io.netty.buffer.ByteBuf
import com.github.mauricio.async.db.mysql.column.ColumnTypes
import java.time.{Duration => JavaDuration}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

object DurationEncoder extends BinaryEncoder {

  private final val Zero = JavaDuration.ZERO

  private def asJavaDuration(value: Any): JavaDuration = value match {
    case duration: JavaDuration   => duration
    case duration: FiniteDuration => JavaDuration.ofNanos(duration.toNanos)
    case duration: ScalaDuration if duration.isFinite =>
      JavaDuration.ofNanos(duration.toNanos)
    case duration: ScalaDuration =>
      throw new IllegalArgumentException(
        s"MySQL TIME does not support non-finite Scala durations: $duration"
      )
  }

  def encode(value: Any, buffer: ByteBuf) = {
    val duration = asJavaDuration(value)
    val absolute = if (duration.isNegative) duration.negated() else duration

    val days            = absolute.toDays
    val hoursDuration   = absolute.minusDays(days)
    val hours           = hoursDuration.toHours
    val minutesDuration = hoursDuration.minusHours(hours)
    val minutes         = minutesDuration.toMinutes
    val secondsDuration = minutesDuration.minusMinutes(minutes)
    val seconds         = secondsDuration.getSeconds
    val micros          = secondsDuration.minusSeconds(seconds).getNano / 1000

    val hasMicros = micros != 0

    if (hasMicros) {
      buffer.writeByte(12)
    } else {
      buffer.writeByte(8)
    }

    if (duration.compareTo(Zero) >= 0) {
      buffer.writeByte(0)
    } else {
      buffer.writeByte(1)
    }

    buffer.writeInt(days.asInstanceOf[Int])
    buffer.writeByte(hours.asInstanceOf[Int])
    buffer.writeByte(minutes.asInstanceOf[Int])
    buffer.writeByte(seconds.asInstanceOf[Int])

    if (hasMicros) {
      buffer.writeInt(micros.asInstanceOf[Int])
    }

  }

  def encodesTo: Int = ColumnTypes.FIELD_TYPE_TIME
}
