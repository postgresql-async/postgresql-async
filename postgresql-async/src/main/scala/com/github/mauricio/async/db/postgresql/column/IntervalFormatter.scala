package com.github.mauricio.async.db.postgresql.column

import scala.util.matching.Regex

object IntervalFormatter {

  val g1: Regex =
    "([-+]?)P(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?(T)?(?:([-+]?[0-9]+)H)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)S)?"
      .r(
        "sign",
        "year",
        "month",
        "week",
        "day",
        "T",
        "hour",
        "minute",
        "second"
      )
  val s1      = "P1Y2M3DT4H5M6S"
  val matcher = g1.pattern.matcher("P1Y2M3DT4H5M6S")
  matcher.start()

  // val g2 = "@(\\s[-+]?[0-9]+ (day|days))    4 hours 5 mins 6 secs"
  val g2 =
    "@(?:(\\s[-+]?[0-9])+\\s(days|day))?(?:(\\s[-+]?[0-9])+\\s(hours|hour))?".r

  val g =
    "(?:@\\s)?(?:([-+]?[0-9])+\\s(?:days|day)\\s?)?(?:([-+]?[0-9])+\\s(?:mons|mon))?"

}
