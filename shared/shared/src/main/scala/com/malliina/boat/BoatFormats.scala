package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM, Temperature}

import scala.concurrent.duration.Duration

object BoatFormats:
  def formatSpeed(s: SpeedM) = "%.3f".format(s.toKnots)
  def formatSpeedKph(s: SpeedM) = "%.3f".format(s.toKmh)
  def formatDistance(d: DistanceM) = "%.3f".format(d.toKilometers)
  def formatTemp(t: Temperature) = "%.1f".format(t.toCelsius)

  def formatDuration(d: Duration): String =
    val parts = split(d)
    if parts.days > 0 then
      "%d:%d:%02d:%02d".format(parts.days, parts.hours, parts.minutes, parts.seconds)
    else if parts.hours > 0 then "%d:%02d:%02d".format(parts.hours, parts.minutes, parts.seconds)
    else "%02d:%02d".format(parts.minutes, parts.seconds)

  def durationHuman(d: Duration): String =
    val parts = split(d)
    val days = if parts.days > 0 then s"${parts.days} d " else ""
    val hours = if parts.hours > 0 then s"${parts.hours} h " else ""
    s"$days$hours${parts.minutes} m ${parts.seconds} s"

  def inHours(d: Duration): String = s"${d.toHours} h"

  def split(d: Duration) =
    val seconds = d.toSeconds
    DurationParts(
      seconds / (60 * 60 * 24),
      (seconds / (60 * 60)) % 24,
      (seconds / 60) % 60,
      seconds % 60
    )

case class DurationParts(days: Long, hours: Long, minutes: Long, seconds: Long)
