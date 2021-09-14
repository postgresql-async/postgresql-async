package com.github.mauricio.async.db.postgresql.column

import scala.concurrent.duration._

import org.specs2.mutable.Specification

class PostgreSQLIntervalEncoderDecoderV2Spec extends Specification {

  import PostgreSQLIntervalEncoderDecoderV2Spec._

  val codec = PostgreSQLIntervalEncoderDecoderV2

  "PostgreSQLIntervalEncoderDecoder" should {
    "decode postgres_verbose intervals" in {
      val cases = List(
        "@ 3 days 4 hours 5 mins 6 secs" -> "P3DT4H5M6S",
        "@ 1 year 2 mons -3 days 4 hours 5 mins 6 secs ago" -> "P1Y1M27DT4H5M6S",
        "@ 1 year 2 mons" -> "P1Y2M"
      )
      cases forall { case (x, y) =>
        codec.decode(x).show === y
      }
    }
  }

  "decode postgres intervals" in {
    val cases = List(
      "1 year 2 mons"                     -> "P1Y2M",
      "3 days 04:05:06"                   -> "P3DT4H5M6S",
      "-1 year -2 mons +3 days -04:05:06" -> "-P1Y1M26DT19H54M54S"
    )
    cases forall { case (x, y) =>
      codec.decode(x).show === y
    }
  }
}

object PostgreSQLIntervalEncoderDecoderV2Spec {
  implicit class DurationShow(d: Duration) {
    def show: String =
      if (d.isFinite) {
        if (d.toMillis < 0) "-" + showPositive(Duration.Zero.minus(d))
        else showPositive(d)
      } else d.toString

    private def showPositive(d: Duration): String =
      if (d.isFinite) {
        val years   = if (d.toDays >= 365) d.toDays / 365 else 0
        val d1      = d.minus((years * 365).days)
        val months  = if (d1.toDays >= 30) d1.toDays / 30 else 0
        val d2      = d1.minus((months * 30).days)
        val days    = d2.toDays
        val d3      = d2.minus(days.days)
        val hours   = d3.toHours
        val d4      = d3.minus(hours.hours)
        val minutes = d4.toMinutes
        val d5      = d4.minus(minutes.minutes)
        val seconds = d5.toSeconds
        val Y       = if (years != 0) s"${years}Y" else ""
        val M       = if (months != 0) s"${months}M" else ""
        val D       = if (days != 0) s"${days}D" else ""
        val T       = if (hours + minutes + seconds != 0) "T" else ""
        val H       = if (hours != 0) s"${hours}H" else ""
        val m       = if (minutes != 0) s"${minutes}M" else ""
        val s       = if (seconds != 0) s"${seconds}S" else ""
        s"""P$Y$M$D$T$H$m$s"""
      } else d.toString
  }
}
