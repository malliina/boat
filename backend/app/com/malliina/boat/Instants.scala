package com.malliina.boat

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

/** Converts java.time.* types to primitives for Scala.js.
  *
  * Scala.js does not support java.time.* fully.
  */
object Instants extends TimeFormatter(TimePatterns.fi)

case class TimePatterns(date: String, time: String, dateTime: String)

object TimePatterns {
  val fi = TimePatterns("yyyy-MM-dd", "HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
  val se = TimePatterns("yyyy-MM-dd", "HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
  val en = TimePatterns("dd MMM yyyy", "HH:mm:ss", "dd MMM yyyy HH:mm:ss")
}

object TimeFormatter {
  def apply(patterns: TimePatterns) = new TimeFormatter(patterns)

  def apply(language: Language): TimeFormatter = language match {
    case Language.finnish => fi
    case Language.swedish => se
    case Language.english => en
    case _ => en
  }

  val fi = apply(TimePatterns.fi)
  val se = apply(TimePatterns.se)
  val en = apply(TimePatterns.en)
}

class TimeFormatter(patterns: TimePatterns) {
  val dateFormatter = DateTimeFormatter
    .ofPattern(patterns.date)
    .withZone(ZoneId.systemDefault())

  val timeFormatter = DateTimeFormatter
    .ofPattern(patterns.time)
    .withZone(ZoneId.systemDefault())

  val dateTimeFormatter = DateTimeFormatter
    .ofPattern(patterns.dateTime)
    .withZone(ZoneId.systemDefault())

  def formatDate(i: Instant) = FormattedDate(dateFormatter.format(i))

  def formatTime(i: Instant) = FormattedTime(timeFormatter.format(i))

  def formatDateTime(i: Instant): FormattedDateTime = FormattedDateTime(dateTimeFormatter.format(i))

  def formatRange(start: Instant, end: Instant): String =
    s"${formatDateTime(start)} - ${formatTime(end)}"

  def timing(i: Instant) =
    Timing(formatDate(i),
           formatTime(i),
           formatDateTime(i),
           i.toEpochMilli)

  def times(start: Instant, end: Instant): Times = Times(
    timing(start),
    timing(end),
    formatRange(start, end)
  )
}
