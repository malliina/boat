package com.malliina.boat

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

/** Converts java.time.* types to primitives for Scala.js.
  *
  * Scala.js does not support java.time.* fully.
  */
object Instants {

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    //  val formatter = DateTimeFormatter.ISO_DATE_TIME
    .withZone(ZoneId.systemDefault())

  val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

  def format(i: Instant): ISODateTime = ISODateTime(formatter.format(i))

  def formatTime(i: Instant): String = timeFormatter.format(i)

  def formatRange(start: Instant, end: Instant): String =
    s"${format(start)} - ${formatTime(end)}"
}
