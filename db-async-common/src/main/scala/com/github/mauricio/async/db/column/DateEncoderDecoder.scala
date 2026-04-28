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

package com.github.mauricio.async.db.column

import com.github.mauricio.async.db.exceptions.DateEncoderNotAvailableException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateEncoderDecoder extends ColumnEncoderDecoder {

  private val ZeroedDate = "0000-00-00"

  override def decode(value: String): LocalDate =
    if (ZeroedDate == value) {
      null
    } else {
      parseDate(value)
    }

  override def encode(value: Any): String = {
    value match {
      case d: java.sql.Date => formatDate(d.toLocalDate)
      case d: LocalDate     => formatDate(d)
      case _                => throw new DateEncoderNotAvailableException(value)
    }
  }

  /**
   * Optimized date parsing using manual parsing instead of formatter for better
   * performance
   */
  private def parseDate(value: String): LocalDate = {
    if (
      value.length != 10 || value.charAt(4) != '-' || value.charAt(7) != '-'
    ) {
      LocalDate.parse(value, oldFormatter)
    } else {
      val yearOpt  = toIntOption(value.slice(0, 4))
      val monthOpt = toIntOption(value.slice(5, 7))
      val dayOpt   = toIntOption(value.slice(8, 10))

      (yearOpt, monthOpt, dayOpt) match {
        case (Some(year), Some(month), Some(day)) =>
          LocalDate.of(year, month, day)
        case _ =>
          // Fallback to old formatter on any parsing error
          LocalDate.parse(value, oldFormatter)
      }
    }
  }

  /**
   * Optimized date formatting using manual formatting instead of formatter for
   * better performance
   */
  private def formatDate(date: LocalDate): String = {
    val year  = date.getYear
    val month = date.getMonthValue
    val day   = date.getDayOfMonth

    // Use string interpolation for cleaner formatting
    f"$year%04d-$month%02d-$day%02d"
  }

  // Keep old formatter for fallback
  private val oldFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  private def toIntOption(value: String): Option[Int] =
    try {
      Some(value.toInt)
    } catch {
      case _: NumberFormatException => None
    }

}
