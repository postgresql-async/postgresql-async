package com.github.mauricio.async.db.postgresql.util

import com.github.mauricio.async.db.column.JavaTimeSupport
import io.netty.buffer.ByteBuf
import java.nio.charset.Charset
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.time.{
  LocalDateTime,
  OffsetDateTime,
  ZoneId,
  ZoneOffset,
  ZonedDateTime
}
import scala.util.control.NonFatal

private[postgresql] object DateTimeParserHelper {

  private val powerOf10 = Array(1, 10, 100, 1000, 10000, 100000, 1000000,
    10000000, 100000000, 1000000000)

  val timestampFormatter: DateTimeFormatter = {
    new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .optionalStart()
      .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, true)
      .optionalEnd()
      .optionalStart()
      .appendOffset("+HH:mm", "Z")
      .optionalEnd()
      .toFormatter()
  }

  def parseLocalDateTime(text: String): LocalDateTime = {
    LocalDateTime.parse(text, timestampFormatter)
  }

  def parseOffsetDateTime(text: String): OffsetDateTime = {
    JavaTimeSupport.normalizeToSystemZone(
      OffsetDateTime.parse(text, timestampFormatter)
    )
  }

  /** Parse in-place, improve performance, if failed, reader index is reset */
  def fastParseLocalDateTime(
    buf: ByteBuf,
    charset: Charset
  ): Option[LocalDateTime] = {
    if (buf.readableBytes() == 0) { None }
    else {
      try {
        Some(
          parseTimestampFromByteBuf(
            buf,
            false,
            (year, month, day, hour, minute, second, nanos, timezone) =>
              LocalDateTime.of(year, month, day, hour, minute, second, nanos)
          )
        )
      } catch {
        case _: Exception => None
      }
    }
  }

  /** Parse in-place, improve performance, if failed, reader index is reset */
  def fastParseOffsetDateTime(buf: ByteBuf): Option[OffsetDateTime] = {
    if (buf.readableBytes() == 0) {
      None
    } else {
      try {
        parseTimestampFromByteBuf(
          buf,
          true,
          (year, month, day, hour, minute, second, nanos, timezone) =>
            timezone match {
              case Some(zone) =>
                Some(
                  JavaTimeSupport.normalizeToSystemZone(
                    ZonedDateTime
                      .of(year, month, day, hour, minute, second, nanos, zone)
                      .toOffsetDateTime
                  )
                )
              case None => None
            }
        )
      } catch {
        case _: Exception => None
      }
    }
  }

  private def parseTimestampFromByteBuf[T](
    buf: ByteBuf,
    withTimezone: Boolean,
    f: (Int, Int, Int, Int, Int, Int, Int, Option[ZoneId]) => T
  ): T = {
    buf.markReaderIndex()
    try {
      // Parse year (4 digits)
      val year = parseDigits(buf, 4)

      // Skip '-' separator
      buf.skipBytes(1)

      // Parse month (2 digits)
      val month = parseDigits(buf, 2)

      // Skip '-' separator
      buf.skipBytes(1)

      // Parse day (2 digits)
      val day = parseDigits(buf, 2)

      // Skip ' ' separator
      buf.skipBytes(1)

      // Parse hour (2 digits)
      val hour = parseDigits(buf, 2)

      // Skip ':' separator
      buf.skipBytes(1)

      // Parse minute (2 digits)
      val minute = parseDigits(buf, 2)

      // Skip ':' separator
      buf.skipBytes(1)

      // Parse second (2 digits)
      val second = parseDigits(buf, 2)

      // Parse optional fractional seconds
      val nanos =
        if (buf.readableBytes() > 0 && buf.getByte(buf.readerIndex()) == '.') {
          buf.skipBytes(1) // skip '.'
          parseFractionalSeconds(buf)
        } else 0

      // Parse optional timezone
      val timezone = if (withTimezone) {
        Some(parseTimezone(buf))
      } else {
        None
      }

      f(year, month, day, hour, minute, second, nanos, timezone)
    } catch {
      case NonFatal(e) =>
        buf.resetReaderIndex()
        throw e
    }
  }

  private def parseDigits(buf: ByteBuf, count: Int): Int = {
    var result = 0
    var i      = 0
    while (i < count) {
      val digit = buf.readByte() - '0'
      if (digit < 0 || digit > 9) {
        throw new IllegalArgumentException(s"Invalid digit at position $i")
      }
      result = result * 10 + digit
      i = i + 1
    }
    result
  }

  private def parseFractionalSeconds(buf: ByteBuf): Int = {
    var result    = 0
    var digits    = 0
    var readFully = false

    while (!readFully && buf.readableBytes() > 0 && digits <= 6) {
      val b = buf.getByte(buf.readerIndex())
      if (b >= '0' && b <= '9') {
        val digit = buf.readByte() - '0'
        result = result * 10 + digit
        digits += 1
      } else { // All digits have read, may followed by timezone, skip read
        readFully = true
      }
    }

    // Scale to nanoseconds.
    if (digits <= 9) {
      result * powerOf10(9 - digits)
    } else {
      result / powerOf10(digits - 9)
    }
  }

  private def parseTimezone(buf: ByteBuf): ZoneId = {
    if (buf.readableBytes() == 0) {
      return ZoneOffset.UTC
    } else {

      val firstChar = buf.getByte(buf.readerIndex())
      if (firstChar == '+' || firstChar == '-') {
        // Parse offset timezone (+HH:MM or -HH:MM)
        parseOffsetTimezone(buf)
      } else if (firstChar == 'Z') {
        // UTC timezone
        buf.skipBytes(1)
        ZoneOffset.UTC
      } else {
        // Named timezone - fall back to string parsing
        val bytes = new Array[Byte](buf.readableBytes())
        buf.readBytes(bytes)
        val timezoneStr = new String(bytes)
        ZoneId.of(timezoneStr)
      }
    }
  }

  private def parseOffsetTimezone(buf: ByteBuf): ZoneOffset = {
    val signChar = buf.readByte().toChar
    val sign     = if (signChar == '+') 1 else -1

    val hours = parseDigits(buf, 2)

    // Skip ':' separator if present
    if (buf.readableBytes() > 0 && buf.getByte(buf.readerIndex()) == ':') {
      buf.skipBytes(1)
    }

    val minutes = if (buf.readableBytes() >= 2) parseDigits(buf, 2) else 0

    ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes)
  }

}
