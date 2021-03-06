package com.malliina.boat

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

case class TimePatterns(date: String, time: String, dateTime: String)

object TimePatterns {
  val fi = TimePatterns("dd.MM.yyyy", "HH:mm:ss", "dd.MM.yyyy HH:mm:ss")
  val se = fi
  val en = TimePatterns("dd MMM yyyy", "HH:mm:ss", "dd MMM yyyy HH:mm:ss")
}

object TimeFormatter {
  def apply(patterns: TimePatterns) = new TimeFormatter(patterns)

  def apply(language: Language): TimeFormatter = language match {
    case Language.finnish => fi
    case Language.swedish => se
    case Language.english => en
    case _                => en
  }

  val fi = apply(TimePatterns.fi)
  val se = apply(TimePatterns.se)
  val en = apply(TimePatterns.en)
}

class TimeFormatter(patterns: TimePatterns) {
  val helsinkiZone = ZoneId.of("Europe/Helsinki")
  val dateFormatter = DateTimeFormatter
    .ofPattern(patterns.date)
    .withZone(helsinkiZone)

  val timeFormatter = DateTimeFormatter
    .ofPattern(patterns.time)
    .withZone(helsinkiZone)

  val dateTimeFormatter = DateTimeFormatter
    .ofPattern(patterns.dateTime)
    .withZone(helsinkiZone)

  def formatDate(i: Instant) = FormattedDate(dateFormatter.format(i))

  def formatTime(i: Instant) = FormattedTime(timeFormatter.format(i))

  def formatDateTime(i: Instant): FormattedDateTime = FormattedDateTime(dateTimeFormatter.format(i))

  def formatRange(start: Instant, end: Instant): String =
    s"${formatDateTime(start)} - ${formatTime(end)}"

  def timing(i: Instant) =
    Timing(formatDate(i), formatTime(i), formatDateTime(i), i.toEpochMilli)

  def times(start: Instant, end: Instant): Times = Times(
    timing(start),
    timing(end),
    formatRange(start, end)
  )
}
