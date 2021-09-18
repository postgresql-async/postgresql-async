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
import scala.util.Try
import scala.util.matching.Regex

import com.github.mauricio.async.db.column.ColumnEncoderDecoder
import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException

object RegexIntervalDecoder {
  sealed trait Sign
  case object PositiveSign extends Sign
  case object NegativeSign extends Sign

  sealed abstract class IntervalSegment(
    val label: String,
    val toDuration: Int => FiniteDuration
  )
  case object YmdSign extends IntervalSegment("sign1", _ => Duration.Zero)
  case object Year  extends IntervalSegment("year", (i: Int) => (i * 365).days)
  case object Month extends IntervalSegment("month", (i: Int) => (i * 30).days)
  case object Week  extends IntervalSegment("week", (i: Int) => (i * 7).days)
  case object Day   extends IntervalSegment("day", _.days)
  case object TSegment extends IntervalSegment("t", _.days)
  case object HmsSign  extends IntervalSegment("sign2", _ => Duration.Zero)
  case object Hour     extends IntervalSegment("hour", _.hours)
  case object Minute   extends IntervalSegment("minute", _.minutes)
  case object Second   extends IntervalSegment("second", _.seconds)

  object IntervalSegment {
    val all: List[IntervalSegment] = List(
      YmdSign,
      Year,
      Month,
      Week,
      Day,
      TSegment,
      HmsSign,
      Hour,
      Minute,
      Second
    )
  }

}

trait RegexIntervalDecoder {

  import RegexIntervalDecoder._

  def regex: Regex

  def decode(value: String): FiniteDuration =
    regex.findFirstMatchIn(value) match {
      case None => throw new DateEncoderNotAvailableException(value)
      case Some(m: Regex.Match) => durationOf(m)
    }

  protected def segments: List[IntervalSegment]

  protected val dateSegments: List[IntervalSegment] =
    List(Year, Month, Week, Day)

  protected val timeSegments: List[IntervalSegment] = List(Hour, Minute, Second)

  protected def durationOf(matcher: Regex.Match): FiniteDuration =
    durationOf(dateSegments, matcher, parseSign(matcher, YmdSign.label)) +
      durationOf(timeSegments, matcher, parseSign(matcher, HmsSign.label))

  protected def durationOf(
    segments: Seq[IntervalSegment],
    matcher: Regex.Match,
    sign: Sign
  ): FiniteDuration = {
    val d = segments.flatMap { segment =>
      Option(matcher.group(segment.label)).map { s =>
        segment.toDuration(s.toInt).toMillis
      }
    }.sum
    sign match {
      case RegexIntervalDecoder.PositiveSign => d.milliseconds
      case RegexIntervalDecoder.NegativeSign => (0L - d).milliseconds
    }
  }

  protected def parseSign(matcher: Regex.Match, label: String): Sign =
    Try(Option(matcher.group(label))).toOption.flatten match {
      case Some("+") => PositiveSign
      case Some("-") => NegativeSign
      case _         => PositiveSign
    }

}

object RegexIso8601PeriodDecoder extends RegexIntervalDecoder {

  import RegexIntervalDecoder._

  override protected val segments: List[IntervalSegment] =
    IntervalSegment.all.filterNot(_ == HmsSign)

  private val labels: List[String] = segments.map(_.label)

  override val regex: Regex = {
    "([-+]?)P(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?(T)?(?:([-+]?[0-9]+)H)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)S)?"
      .r(labels: _*)
  }
}

/**
 * @ 1 year 2 mons
 * @ 3 days 4 hours 5 mins 6 secs
 * @ 1 year 2 mons -3 days 4 hours 5 mins 6 secs ago
 */
object RegexPostgresVerboseIntervalDecoder extends RegexIntervalDecoder {

  import RegexIntervalDecoder._

  override val segments: List[RegexIntervalDecoder.IntervalSegment] =
    IntervalSegment.all.filter(s =>
      s != YmdSign && s != TSegment && s != HmsSign
    )

  private val labels = segments.map(_.label)

  override val regex: Regex =
    """(?:@\s)
      |(?:([-+]?[0-9]+)\s(?:years|year)\s?)?
      |(?:([-+]?[0-9]+)\s(?:months|month|mons|mon)\s?)?
      |(?:([-+]?[0-9]+)\s(?:weeks|week)\s?)?
      |(?:([-+]?[0-9]+)\s(?:days|day)\s?)?
      |(?:([-+]?[0-9]+)\s(?:hours|hour)\s?)?
      |(?:([-+]?[0-9]+)\s(?:minutes|minute|mins|min)\s?)?
      |(?:([-+]?[0-9]+)\s(?:seconds|second|secs|sec))?
      |(?:\s(?:ago))?""".stripMargin.replace("\n", "").r(labels: _*)
}

/**
 * 1 year 2 mons 3 days 04:05:06
 * -1 year -2 mons +3 days -04:05:06
 */
object RegexPostgresIntervalDecoder extends RegexIntervalDecoder {

  import RegexIntervalDecoder._

  override val segments: List[RegexIntervalDecoder.IntervalSegment] =
    IntervalSegment.all.filter(s => s != YmdSign && s != TSegment)

  private val labels = segments.map(_.label)

  override val regex: Regex =
    """(?:([-+]?[0-9]+)\s(?:years|year)\s?)?
      |(?:([-+]?[0-9]+)\s(?:months|month|mons|mon)\s?)?
      |(?:([-+]?[0-9]+)\s(?:weeks|week)\s?)?
      |(?:([-+]?[0-9]+)\s(?:days|day),?\s?)?
      |([-+]?)?
      |(?:([-+]?[0-9]+))?
      |(?::)?
      |(?:([-+]?[0-9]{1,2}))?
      |(?::)?
      |(?:([-+]?[0-9]{1,2}))?""".stripMargin.replace("\n", "").r(labels: _*)
}

/**
 * 1-2 3 -4:05:06
 * -1-2 +3 -4:05:06
 */
object RegexSqlStandardIntervalDecoder extends RegexIntervalDecoder {

  import RegexIntervalDecoder._

  protected override val dateSegments: List[IntervalSegment] =
    List(Year, Month, Day)

  override val segments: List[RegexIntervalDecoder.IntervalSegment] =
    List(YmdSign, Year, Month, Day, HmsSign, Hour, Minute, Second)

  private val labels = segments.map(_.label)

  override val regex: Regex =
    """(?:([-+]+)?
      |(?:([0-9]+)-([0-9]+))?
      |)?
      |(?:
      |\s?
      |(?:([-+]?[0-9])+)+
      |\s
      |(?:([-+]?))?
      |(?:([0-9]){1,2})+
      |:
      |(?:([0-9]){1,2})+
      |:
      |(?:([0-9]){1,2})+
      |)?""".stripMargin.replace("\n", "").r(labels: _*)

  protected override def durationOf(matcher: Regex.Match): FiniteDuration =
    durationOf(List(Year, Month), matcher, parseSign(matcher, YmdSign.label)) +
      durationOf(List(Day), matcher, PositiveSign) +
      durationOf(timeSegments, matcher, parseSign(matcher, HmsSign.label))
}

object PostgreSQLIntervalEncoderDecoder extends ColumnEncoderDecoder {

  private val pgPriorityRegex = """^[-+]?[0-9]+:[0-9]{2}:[0-9]{2}$""".r

  override def decode(value: String): Duration = {
    val decoder: RegexIntervalDecoder = value match {
      case s if s.startsWith("P") || s.startsWith("-P") || s.startsWith("+P") =>
        RegexIso8601PeriodDecoder
      case s if s.startsWith("@ ") => RegexPostgresVerboseIntervalDecoder
      case s if pgPriorityRegex.pattern.matcher(s).matches() =>
        RegexPostgresIntervalDecoder
      case s
          if RegexSqlStandardIntervalDecoder.regex.pattern
            .matcher(s)
            .matches() =>
        RegexSqlStandardIntervalDecoder
      case _ => RegexPostgresIntervalDecoder
    }
    decoder.decode(value)
  }

  override def encode(value: Any): String = value match {
    case d: FiniteDuration => s"${d.toSeconds} seconds"
    case _                 => throw new DateEncoderNotAvailableException(value)
  }
}
