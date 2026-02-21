package com.malliina.boat.parsing

import com.malliina.boat.{Energy, InsertedPoint, KeyedSentence, LocationUpdate, RawSentence, SentenceKey, TimeFormatter, TimedCoord, TrackId, TrackMetaShort, TrackPointId, UserAgent}
import com.malliina.geo.Coord
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Degrees, ErrorMessage}

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

sealed trait ParsedSentence:
  def sentence: KeyedSentence
  def from: TrackMetaShort = sentence.from
  def track: TrackId = from.track
  def key: SentenceKey = sentence.key

case class ParsedCoord(coord: Coord, ggaTime: LocalTime, sentence: KeyedSentence)
  extends ParsedSentence:
  def lng = coord.lng
  def lat = coord.lat

  def complete(
    date: LocalDate,
    time: LocalTime,
    boatSpeed: SpeedM,
    waterTemp: Temperature,
    depth: DistanceM,
    depthOffset: DistanceM,
    parts: Seq[SentenceKey],
    userAgent: Option[UserAgent]
  ): FullCoord =
    FullCoord(
      coord,
      time,
      date,
      boatSpeed,
      BoatStats(waterTemp, depth, depthOffset, parts),
      from,
      userAgent
    )

case class ParsedDateTime(date: LocalDate, time: LocalTime, sentence: KeyedSentence)
  extends ParsedSentence

case class ParsedBoatSpeed(speed: SpeedM, sentence: KeyedSentence) extends ParsedSentence

case class ParsedWaterSpeed(speed: SpeedM, sentence: KeyedSentence) extends ParsedSentence

case class WaterTemperature(temp: Temperature, sentence: KeyedSentence) extends ParsedSentence

case class WaterDepth(depth: DistanceM, offset: DistanceM, sentence: KeyedSentence)
  extends ParsedSentence

case class BoatStats(
  waterTemp: Temperature,
  depth: DistanceM,
  depthOffset: DistanceM,
  parts: Seq[SentenceKey] = Nil
)

case class CarStats(
  batteryLevel: Option[Energy],
  rangeRemaining: Option[DistanceM],
  altitude: Option[DistanceM],
  accuracy: Option[DistanceM],
  bearing: Option[Degrees],
  bearingAccuracyDegrees: Option[Degrees],
  outsideTemperature: Option[Temperature],
  nightMode: Option[Boolean]
)

trait PointInsert:
  def coord: Coord
  def lng = coord.lng
  def lat = coord.lat
  def speedOpt: Option[SpeedM]
  def boatStats: Option[BoatStats]
  def carStats: Option[CarStats]
  def sourceTime: Instant
  def track: TrackId
  def userAgent: Option[UserAgent]
  def timed(id: TrackPointId, formatter: TimeFormatter): TimedCoord

case class CarCoord(
  coord: Coord,
  speed: Option[SpeedM],
  stats: CarStats,
  sourceTime: Instant,
  track: TrackId,
  userAgent: Option[UserAgent]
) extends PointInsert:
  override def speedOpt: Option[SpeedM] = speed
  override def boatStats: Option[BoatStats] = None
  override def carStats: Option[CarStats] = Option(stats)

  // Fix the lies
  def timed(id: TrackPointId, formatter: TimeFormatter) = TimedCoord(
    id,
    coord,
    formatter.formatDateTime(sourceTime),
    sourceTime.toEpochMilli,
    formatter.formatTime(sourceTime),
    speed.getOrElse(SpeedM.zero),
    carStats.flatMap(_.altitude),
    carStats.flatMap(_.outsideTemperature),
    Temperature.zeroCelsius,
    DistanceM.zero,
    stats.batteryLevel,
    formatter.timing(sourceTime)
  )

object CarCoord:
  def fromUpdate(loc: LocationUpdate, track: TrackId, userAgent: Option[UserAgent]) = CarCoord(
    loc.coord,
    loc.speed,
    CarStats(
      loc.batteryLevel,
      loc.rangeRemaining,
      loc.altitudeMeters,
      loc.accuracyMeters,
      loc.bearing,
      loc.bearingAccuracyDegrees,
      loc.outsideTemperature,
      loc.nightMode
    ),
    loc.date.toInstant,
    track,
    userAgent
  )

case class FullCoord(
  coord: Coord,
  time: LocalTime,
  date: LocalDate,
  speed: SpeedM,
  boat: BoatStats,
  from: TrackMetaShort,
  userAgent: Option[UserAgent]
) extends PointInsert:
  val dateTime = date.atTime(time)
  val sourceTime = dateTime.toInstant(ZoneOffset.UTC)
  override def speedOpt: Option[SpeedM] = Option(speed)
  override def boatStats: Option[BoatStats] = Option(boat)
  override def carStats: Option[CarStats] = None
  override def track: TrackId = from.track

  def timed(id: TrackPointId, formatter: TimeFormatter): TimedCoord = TimedCoord(
    id,
    coord,
    formatter.formatDateTime(sourceTime),
    sourceTime.toEpochMilli,
    formatter.formatTime(sourceTime),
    speed,
    carStats.flatMap(_.altitude),
    carStats.flatMap(_.outsideTemperature),
    boat.waterTemp,
    boat.depth,
    carStats.flatMap(_.batteryLevel),
    formatter.timing(sourceTime)
  )

sealed trait SentenceError:
  def sentence: RawSentence
  def message: ErrorMessage
  def messageString = message.message

case class InvalidSentence(sentence: RawSentence, message: ErrorMessage) extends SentenceError

case class UnknownSentence(sentence: RawSentence, detailedMessage: String) extends SentenceError:
  override def message: ErrorMessage = ErrorMessage(
    s"Unknown sentence: '$sentence'. $detailedMessage"
  )

case class SuspectTime(sentence: RawSentence) extends SentenceError:
  override def message: ErrorMessage =
    ErrorMessage(s"Suspect time in '$sentence'. This might mean the plotter is still initializing.")

case class SentenceFailure(sentence: RawSentence, e: Exception) extends SentenceError:
  override def message: ErrorMessage = ErrorMessage(
    s"Error for sentence: '$sentence'. ${e.getMessage}"
  )

case class IgnoredSentence(sentence: RawSentence) extends SentenceError:
  override def message = ErrorMessage(s"Ignoring sentence '$sentence'.")

sealed trait SavedEvent
case object EmptySavedEvent extends SavedEvent
case class InsertedCoord(coord: PointInsert, inserted: InsertedPoint) extends SavedEvent
