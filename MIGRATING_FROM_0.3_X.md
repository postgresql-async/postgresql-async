# Migrating From 0.3.x

This document covers driver-facing time API changes when upgrading from the `0.3.x` line in this repository.

## Scope

This is a repo-level migration note for both drivers:

- `postgresql-async`
- `mysql-async`

The main behavioral change in this branch is PostgreSQL `interval` handling. MySQL time mappings are documented here as
well so users upgrading either driver can verify what changed and what did not.

## Summary

### PostgreSQL

Compared with `0.3.x`:

- PostgreSQL date/time values now use `java.time` instead of Joda-Time
- `interval` query results now decode to raw PostgreSQL text as `String`
- `interval` prepared statement parameters now accept `java.time.Period` and `java.time.Duration`
- Joda `ReadablePeriod` and `ReadableDuration` are no longer accepted for PostgreSQL `interval` parameters
- `timestamp with time zone` results are normalized to the JVM system zone while preserving the instant
- `time with time zone` prepared statement parameters accept `java.time.OffsetTime`

PostgreSQL date/time mappings are now:

- `timestamp` -> `java.time.LocalDateTime`
- `timestamp_with_timezone` -> `java.time.OffsetDateTime`
- `date` -> `java.time.LocalDate`
- `time` -> `java.time.LocalTime`
- `time_with_timezone` -> `java.time.OffsetTime`

### MySQL

MySQL date/time mappings remain:

- `date` -> `java.time.LocalDate`
- `datetime` -> `java.time.LocalDateTime`
- `timestamp` -> `java.time.LocalDateTime`
- `time` -> `java.time.Duration`

Compared with `0.3.x`:

- MySQL date/time values now use `java.time` instead of Joda-Time
- MySQL `time` values now decode to `java.time.Duration` instead of `scala.concurrent.Duration`
- `java.time.OffsetDateTime` prepared statement parameters are encoded by instant in the JVM system zone

## PostgreSQL Migration

### Reading `interval` values

In `0.3.x`:

```scala
val period = row("duration").asInstanceOf[org.joda.time.Period]
```

Now:

```scala
val intervalText = row("duration").asInstanceOf[String]
```

Example PostgreSQL output strings:

- `1 year 2 mons 3 days`
- `04:05:06`
- `1 year 2 mons 3 days 04:05:06`

If your application needs a structured duration type on read, convert the returned text explicitly in application code.

### Writing `interval` values

In `0.3.x`:

```scala
import org.joda.time.Period

connection.sendPreparedStatement(
  "INSERT INTO intervals (duration) VALUES (?)",
  Array(Period.months(2).plusDays(3))
)
```

Now:

```scala
import java.time.{Duration, Period}

connection.sendPreparedStatement(
  "INSERT INTO intervals (duration) VALUES (?)",
  Array(Period.of(0, 2, 3))
)

connection.sendPreparedStatement(
  "INSERT INTO intervals (duration) VALUES (?)",
  Array(Duration.ofHours(4).plusMinutes(5).plusSeconds(6))
)
```

The driver sends both values using their ISO-8601 string form:

- `Period.of(1, 2, 3)` -> `P1Y2M3D`
- `Duration.ofHours(4).plusMinutes(5).plusSeconds(6)` -> `PT4H5M6S`

PostgreSQL stores those as `interval` and returns normalized text when queried.

### PostgreSQL Compatibility Notes

- Code that casts timestamp/date/time results to Joda types must be updated to `java.time`.
- Code that casts `interval` query results to `org.joda.time.Period` must be updated.
- Code that binds Joda `ReadablePeriod` or `ReadableDuration` must be migrated to `java.time.Period` or
  `java.time.Duration`.
- If your code relied on the driver normalizing PostgreSQL interval text into Joda `Period`, that conversion now needs
  to happen outside the driver.
- If your code compared `timestamp with time zone` values by offset or expected the original textual timezone to be
  preserved, update it to compare by instant instead.
- PostgreSQL `timestamp with time zone` stores an instant, not the original offset. The driver now consistently returns
  `OffsetDateTime` normalized to the JVM system zone across scalar, array, and string decoding paths.
- PostgreSQL `time with time zone` parameters should now be passed as `java.time.OffsetTime`.

## MySQL Migration

MySQL now uses `java.time.LocalDate` and `java.time.LocalDateTime` instead of Joda types for decoded results and
prepared statement parameters. MySQL `time` now decodes to `java.time.Duration`.

When writing MySQL `datetime`/`timestamp` values with `java.time.OffsetDateTime`, the driver converts them by instant
into the JVM system zone before encoding. As with PostgreSQL `timestamp with time zone`, applications should not rely
on the original offset being preserved through a round-trip.

If you use both drivers in the same application, the practical migration difference is:

- PostgreSQL and MySQL date/time value handling should move from Joda-Time to `java.time`
- PostgreSQL `interval` write paths should move to `java.time.Period` and `java.time.Duration`
- MySQL `time` now decodes to `java.time.Duration`
- For timezone-aware values, compare by instant rather than expecting the original offset text to round-trip unchanged
