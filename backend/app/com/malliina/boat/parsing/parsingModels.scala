package com.malliina.boat.parsing

import java.time.{LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.{Coord, DeviceId, GPSKeyedSentence, GPSPointId, GPSSentenceKey, GPSTimedCoord, KeyedSentence, RawSentence, SentenceKey, TimeFormatter, TimedCoord, TrackId, TrackMetaShort, TrackPointId}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.ErrorMessage

trait ParsedGPSSentence {
  def sentence: GPSKeyedSentence
  def boat = sentence.from
  def key = sentence.key
}

case class ParsedGPSCoord(coord: Coord, ggaTime: LocalTime, sentence: GPSKeyedSentence)
  extends ParsedGPSSentence {
  def complete(
    date: LocalDate,
    time: LocalTime,
    satellites: Int,
    fix: GPSFix,
    parts: Seq[GPSSentenceKey]
  ) =
    GPSCoord(coord, date, time, satellites, fix, boat, parts)
}

case class ParsedGPSDateTime(date: LocalDate, time: LocalTime, sentence: GPSKeyedSentence)
  extends ParsedGPSSentence

case class SatellitesInView(satellites: Int, sentence: GPSKeyedSentence) extends ParsedGPSSentence

case class GPSInfo(mode: GPSMode, fix: GPSFix, sentence: GPSKeyedSentence) extends ParsedGPSSentence

sealed trait ParsedSentence {
  def sentence: KeyedSentence
  def from: TrackMetaShort = sentence.from
  def track: TrackId = from.track
  def key: SentenceKey = sentence.key
}

case class ParsedCoord(coord: Coord, ggaTime: LocalTime, sentence: KeyedSentence)
  extends ParsedSentence {
  def lng = coord.lng
  def lat = coord.lat

  def complete(
    date: LocalDate,
    time: LocalTime,
    boatSpeed: SpeedM,
    waterTemp: Temperature,
    depth: DistanceM,
    depthOffset: DistanceM,
    parts: Seq[SentenceKey]
  ): FullCoord =
    FullCoord(coord, time, date, boatSpeed, waterTemp, depth, depthOffset, from, parts)
}

case class ParsedDateTime(date: LocalDate, time: LocalTime, sentence: KeyedSentence)
  extends ParsedSentence

case class ParsedBoatSpeed(speed: SpeedM, sentence: KeyedSentence) extends ParsedSentence

case class ParsedWaterSpeed(speed: SpeedM, sentence: KeyedSentence) extends ParsedSentence

case class WaterTemperature(temp: Temperature, sentence: KeyedSentence) extends ParsedSentence

case class WaterDepth(depth: DistanceM, offset: DistanceM, sentence: KeyedSentence)
  extends ParsedSentence

case class GPSCoord(
  coord: Coord,
  date: LocalDate,
  time: LocalTime,
  satellites: Int,
  fix: GPSFix,
  device: DeviceId,
  parts: Seq[GPSSentenceKey] = Nil
) {
  val dateTime = date.atTime(time)
  val gpsTime = dateTime.toInstant(ZoneOffset.UTC)

  def lng = coord.lng
  def lat = coord.lat

  def timed(id: GPSPointId, formatter: TimeFormatter) = GPSTimedCoord(
    id,
    coord,
    formatter.timing(gpsTime)
  )
}

case class FullCoord(
  coord: Coord,
  time: LocalTime,
  date: LocalDate,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depth: DistanceM,
  depthOffset: DistanceM,
  from: TrackMetaShort,
  parts: Seq[SentenceKey] = Nil
) {
  val dateTime = date.atTime(time)
  val boatTime = dateTime.toInstant(ZoneOffset.UTC)

  def lng = coord.lng

  def lat = coord.lat

  def timed(id: TrackPointId, formatter: TimeFormatter): TimedCoord = TimedCoord(
    id,
    coord,
    formatter.formatDateTime(boatTime),
    boatTime.toEpochMilli,
    formatter.formatTime(boatTime),
    boatSpeed,
    waterTemp,
    depth,
    formatter.timing(boatTime)
  )
}

sealed trait SentenceError {
  def sentence: RawSentence
  def message: ErrorMessage
  def messageString = message.message
}

case class InvalidSentence(sentence: RawSentence, message: ErrorMessage) extends SentenceError

case class UnknownSentence(sentence: RawSentence, detailedMessage: String) extends SentenceError {
  override def message: ErrorMessage = ErrorMessage(
    s"Unknown sentence: '$sentence'. $detailedMessage"
  )
}

case class SuspectTime(sentence: RawSentence) extends SentenceError {
  override def message: ErrorMessage =
    ErrorMessage(s"Suspect time in '$sentence'. This might mean the plotter is still initializing.")
}

case class SentenceFailure(sentence: RawSentence, e: Exception) extends SentenceError {
  override def message: ErrorMessage = ErrorMessage(
    s"Error for sentence: '$sentence'. ${e.getMessage}"
  )
}

case class IgnoredSentence(sentence: RawSentence) extends SentenceError {
  override def message = ErrorMessage(s"Ignoring sentence '$sentence'.")
}
