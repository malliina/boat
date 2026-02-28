package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.{Duration, DurationDouble, FiniteDuration}

object BoatFormats:
  given durationDouble: Codec[FiniteDuration] = Codec.from[FiniteDuration](
    Decoder.decodeDouble.map(_.seconds),
    Encoder.encodeDouble.contramap(fd => 1.0d * fd.toMillis / 1000)
  )

  given durationFormat: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeDouble.map(_.seconds),
    Encoder.encodeDouble.contramap(_.toSeconds.toDouble)
  )

  def formatSpeed(s: SpeedM, source: SourceType, includeUnit: Boolean) =
    val unitSuffix =
      if includeUnit then s" ${speedUnit(source)}"
      else ""

    val rounded = source match
      case SourceType.Vehicle  => formatKph(s)
      case SourceType.Boat     => formatKnots(s)
      case SourceType.Other(n) => formatKph(s)
    s"$rounded$unitSuffix"

  def speedUnit(source: SourceType) =
    if source == SourceType.Vehicle then "km/h"
    else "kn"

  def formatKnots(s: SpeedM) = "%.3f".format(s.toKnots)
  def formatKph(s: SpeedM) = "%.3f".format(s.toKmh)
  def formatDistance(d: DistanceM) = "%.3f km".format(d.toKilometers)
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
