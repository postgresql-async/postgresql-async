/*
 * Copyright 2013 Maurício Linhares
 * Copyright 2013 Dylan Simon
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

package com.github.mauricio.async.db.postgresql.column

import scala.concurrent.duration._
import scala.util.matching.Regex

import com.github.mauricio.async.db.column.ColumnEncoderDecoder
import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException

trait IntervalSerializer {
  def regex: Regex

  def decode(value: String): FiniteDuration

  def encode(value: FiniteDuration): String =
    s"${value.toMillis / 1000} seconds"

  protected val fullGroupNamesFn: List[(String, Int => FiniteDuration)] =
    List[(String, Int => FiniteDuration)](
      "sign"   -> (_ => Duration.Zero),
      "year"   -> ((i: Int) => (i * 365).days),
      "month"  -> ((i: Int) => (i * 30).days),
      "week"   -> ((i: Int) => (i * 7).days),
      "day"    -> ((i: Int) => i.days),
      "T"      -> (_ => Duration.Zero),
      "hour"   -> ((i: Int) => i.hours),
      "minute" -> ((i: Int) => i.minutes),
      "second" -> ((i: Int) => i.seconds)
    )
}

object ISOPeriodSerializer extends IntervalSerializer {

  private val groupNamesFn: List[(String, Int => FiniteDuration)] =
    fullGroupNamesFn.filter { case (k, _) =>
      k != "sign" && k != "T"
    }

  private val fullGroupNames: List[String] = fullGroupNamesFn.map(_._1)

  override val regex: Regex = {
    "([-+]?)P(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?(T)?(?:([-+]?[0-9]+)H)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)S)?"
      .r(fullGroupNames: _*)
  }

  override def decode(value: String): FiniteDuration =
    regex.findFirstMatchIn(value) match {
      case None => throw new DateEncoderNotAvailableException(value)
      case Some(m) =>
        val d: Long = groupNamesFn.flatMap { case (g, f) =>
          Option(m.group(g)).map(s => f(s.toInt).toMillis)
        }.sum
        Option(m.group("sign")) match {
          case Some("-") => (0L - d).milliseconds
          case _         => d.milliseconds
        }
    }
}

trait PostgresIntervalSerializer extends IntervalSerializer {

  protected val groupNamesFn: List[(String, Int => FiniteDuration)] =
    fullGroupNamesFn.filter { case (k, _) =>
      k != "sign" && k != "T"
    }

  protected val groupNames: List[String] = groupNamesFn.map(_._1)

  override def decode(value: String): FiniteDuration =
    regex.findFirstMatchIn(value) match {
      case None => throw new DateEncoderNotAvailableException(value)
      case Some(m) =>
        groupNamesFn.flatMap { case (g, f) =>
          Option(m.group(g)).map(s => f(s.toInt).toMillis)
        }.sum.milliseconds
    }
}

object PostgresStandardIntervalSerializer extends PostgresIntervalSerializer {
  override val regex: Regex =
    "(?:@\\s)?(?:([-+]?[0-9]+)\\s(?:years|year)\\s?)?(?:([-+]?[0-9]+)\\s(?:months|month|mons|mon)\\s?)?(?:([-+]?[0-9]+)\\s(?:weeks|week)\\s?)?(?:([-+]?[0-9]+)\\s(?:days|day)\\s?)?(?:([-+]?[0-9]+)\\s(?:hours|hour)\\s?)?(?:([-+]?[0-9]+)\\s(?:minutes|minute|mins|min)\\s?)?(?:([-+]?[0-9]+)\\s(?:seconds|second|secs|sec)\\s?)?"
      .r(groupNames: _*)
}

object PostgresHmsIntervalSerializer extends PostgresIntervalSerializer {
  override val regex: Regex =
    "(?:@\\s)?(?:([-+]?[0-9]+)\\s(?:years|year)\\s?)?(?:([-+]?[0-9]+)\\s(?:months|month|mons|mon)\\s?)?(?:([-+]?[0-9]+)\\s(?:weeks|week)\\s?)?(?:([-+]?[0-9]+)\\s(?:days|day),?\\s?)?(?:([-+]?[0-9])*:)?(?:([-+]?[0-9])*:)?(?:([-+]?[0-9]*))?"
      .r(groupNames: _*)
}

object SqlStandardYmIntervalSerializer extends IntervalSerializer {

  private val groupNamesFn: List[(String, Int => FiniteDuration)] =
    fullGroupNamesFn.collect { case x @ (("year", _) | ("month", _)) => x }

  private val groupNames: List[String] = groupNamesFn.map(_._1)

  override val regex: Regex = "([0-9]+)-([0-9]+)".r(groupNames: _*)

  override def decode(value: String): FiniteDuration =
    regex.findFirstMatchIn(value) match {
      case None => throw new DateEncoderNotAvailableException(value)
      case Some(m) =>
        groupNamesFn.flatMap { case (g, f) =>
          Option(m.group(g)).map(s => f(s.toInt).toMillis)
        }.sum.milliseconds
    }
}

object SqlStandardDhmsIntervalSerializer extends IntervalSerializer {
  private val groupNamesFn: List[(String, Int => FiniteDuration)] =
    fullGroupNamesFn.takeRight(4)

  private val groupNames: List[String] = groupNamesFn.map(_._1)

  override val regex: Regex =
    "(?:([-+]?[0-9]+)\\s?)?(?:([-+]?[0-9])*:)?(?:([-+]?[0-9])*:)?(?:([-+]?[0-9]*))?"
      .r(groupNames: _*)

  override def decode(value: String): FiniteDuration =
    regex.findFirstMatchIn(value) match {
      case None => throw new DateEncoderNotAvailableException(value)
      case Some(m) =>
        groupNamesFn.flatMap { case (g, f) =>
          Option(m.group(g)).map(s => f(s.toInt).toMillis)
        }.sum.milliseconds
    }
}

object PostgreSQLIntervalEncoderDecoderV2 extends ColumnEncoderDecoder {

  private val sqlYmRegex = "^[0-9]+-[0-9]+$".r

  private val sqlDhmsRegex = "^[0-9]+\\s[0-9]+:?[0-9]+:?[0-9]+$".r

  override def decode(value: String): Duration = {
    val serializer: IntervalSerializer = value match {
      case s if s.startsWith("P") || s.startsWith("-P") || s.startsWith("+P") =>
        ISOPeriodSerializer
      case s if s.startsWith("@ ")      => PostgresStandardIntervalSerializer
      case s if sqlYmRegex.matches(s)   => SqlStandardYmIntervalSerializer
      case s if sqlDhmsRegex.matches(s) => SqlStandardDhmsIntervalSerializer
      case s if s.contains(":")         => PostgresHmsIntervalSerializer
      case _                            => PostgresStandardIntervalSerializer
    }
    serializer.decode(value)
  }
}
