package com.malliina.boat

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

/** Converts java.time.* types to primitives for Scala.js.
  *
  * Scala.js does not support java.time.* fully.
  */
object Instants {
  val dateFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd")
    .withZone(ZoneId.systemDefault())

  val dateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  val timeFormatter = DateTimeFormatter
    .ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  def formatDate(i: Instant) = FormattedDate(dateFormatter.format(i))

  def formatDateTime(i: Instant): FormattedDateTime = FormattedDateTime(dateTimeFormatter.format(i))

  def formatTime(i: Instant) = FormattedTime(timeFormatter.format(i))

  def formatRange(start: Instant, end: Instant): String =
    s"${formatDateTime(start)} - ${formatTime(end)}"

  def timing(i: Instant) =
    Timing(Instants.formatDate(i),
           Instants.formatTime(i),
           Instants.formatDateTime(i),
           i.toEpochMilli)

  def times(start: Instant, end: Instant): Times = Times(
    timing(start),
    timing(end),
    Instants.formatRange(start, end)
  )
}
