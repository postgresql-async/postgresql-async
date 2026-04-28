package com.github.mauricio.async.db.column

import java.time.{LocalDateTime, OffsetDateTime, ZoneId}

private[db] object JavaTimeSupport {

  private lazy val systemZone = ZoneId.systemDefault()

  def normalizeToSystemZone(value: OffsetDateTime): OffsetDateTime =
    value.atZoneSameInstant(systemZone).toOffsetDateTime

  def toSystemLocalDateTime(value: OffsetDateTime): LocalDateTime =
    value.toInstant.atZone(systemZone).toLocalDateTime

}
