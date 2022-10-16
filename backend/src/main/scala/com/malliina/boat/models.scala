package com.malliina.boat

import cats.data.NonEmptyList

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}
import com.malliina.boat.BoatPrimitives.durationFormat
import com.malliina.boat.parsing.{FullCoord, GPSCoord, GPSFix}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.*
import com.malliina.web.JWTError
import doobie.Meta
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.EncoderOps

import scala.concurrent.duration.FiniteDuration

case class CSRFToken(token: String) extends AnyVal

case class Languages(finnish: Lang, swedish: Lang, english: Lang)

object Languages:
  implicit val json: Codec[Languages] = deriveCodec[Languages]

case class AisConf(vessel: String, trail: String, vesselIcon: String)

object AisConf:
  implicit val json: Codec[AisConf] = deriveCodec[AisConf]

case class Layers(
  marks: Seq[String],
  fairways: Seq[String],
  fairwayAreas: Seq[String],
  limits: Seq[String],
  ais: AisConf
)

object Layers:
  implicit val json: Codec[Layers] = deriveCodec[Layers]
  import MapboxStyles.*

  val default = Layers(
    marksLayers,
    fairwayLayers,
    Seq(FairwayAreaId),
    LimitLayerIds,
    AisConf(AisVesselLayer, AisTrailLayer, AisVesselIcon)
  )

case class ClientConf(map: MapConf, languages: Languages, layers: Layers)

object ClientConf:
  implicit val json: Codec[ClientConf] = deriveCodec[ClientConf]
  val default = withMap(MapConf.active)
  val old = withMap(MapConf.old)

  def withMap(map: MapConf) =
    ClientConf(map, Languages(Lang.fi, Lang.se, Lang.en), Layers.default)

/** Alternative to LocalDate because according to its Javadoc reference equality and other
  * operations may have unpredictable results whereas this class has predictable structural equality
  * properties.
  */
case class DateVal(year: YearVal, month: MonthVal, day: DayVal):
  def toLocalDate = LocalDate.of(year.year, month.month, day.day)
  def plusDays(days: Int) = DateVal(toLocalDate.plusDays(days))
  def plusMonths(months: Int) = DateVal(toLocalDate.plusMonths(months))
  def plusYears(years: Int) = DateVal(toLocalDate.plusYears(years))
  def iso8601 = toLocalDate.toString
  override def toString = iso8601

object DateVal:
  implicit val json: Codec[DateVal] = Codec.from(
    Decoder.decodeLocalDate.map(apply),
    Encoder.encodeLocalDate.contramap(dv => dv.toLocalDate)
  )
  def now(): DateVal = apply(LocalDate.now())
  def apply(date: LocalDate): DateVal =
    DateVal(
      YearVal(date.getYear),
      MonthVal(date.getMonthValue),
      DayVal(date.getDayOfMonth)
    )

case class UserToken(token: String) extends AnyVal with WrappedString:
  override def value = token

object UserToken extends BoatStringCompanion[UserToken]:
  val minLength = 3

  override def build(in: String): Either[ErrorMessage, UserToken] =
    if in.length >= minLength then Right(UserToken(in))
    else
      Left(
        ErrorMessage(
          s"Too short token. Minimum length: $minLength, was: ${in.length}."
        )
      )

  def random(): UserToken = UserToken(Utils.randomString(length = 8))

case class AppMeta(name: String, version: String, gitHash: String, mapboxVersion: String)

object AppMeta:
  implicit val json: Codec[AppMeta] = deriveCodec[AppMeta]
  val default =
    AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash, BuildInfo.mapboxVersion)

case class JoinedTrack(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  canonical: TrackCanonical,
  comments: Option[String],
  trackAdded: Instant,
  points: Int,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  distance: DistanceM,
  start: Option[Instant],
  startDate: DateVal,
  startMonth: MonthVal,
  startYear: YearVal,
  end: Option[Instant],
  duration: FiniteDuration,
  topSpeed: Option[SpeedM],
  tip: CombinedCoord,
  boat: JoinedBoat
) extends TrackLike:
  def boatId = boat.device
  def language = boat.language
  def user = boat.user
  override def boatName = boat.boatName
  override def username = boat.username

  val startOrNow = start.getOrElse(Instant.now())
  val endOrNow = end.getOrElse(Instant.now())

  /** @return
    *   a Scala.js -compatible representation of this track
    */
  def strip(formatter: TimeFormatter) = TrackRef(
    track,
    trackName,
    trackTitle,
    canonical,
    comments,
    boat.device,
    boatName,
    username,
    points.toInt,
    duration,
    distance,
    topSpeed,
    avgSpeed,
    avgWaterTemp,
    tip.timed(formatter),
    formatter.times(startOrNow, endOrNow)
  )

case class Stats(
  label: String,
  from: DateVal,
  to: DateVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long
)

object Stats:
  implicit val json: Codec[Stats] = deriveCodec[Stats]

case class MonthlyStats(
  label: String,
  year: YearVal,
  month: MonthVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long
)

object MonthlyStats:
  implicit val json: Codec[MonthlyStats] = deriveCodec[MonthlyStats]

case class YearlyStats(
  label: String,
  year: YearVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long,
  monthly: Seq[MonthlyStats]
)

object YearlyStats:
  implicit val json: Codec[YearlyStats] = deriveCodec[YearlyStats]

case class StatsResponse(daily: Seq[Stats], yearly: Seq[YearlyStats], allTime: Stats)

object StatsResponse:
  implicit val json: Codec[StatsResponse] = deriveCodec[StatsResponse]

case class TracksBundle(tracks: Seq[TrackRef], stats: StatsResponse)

object TracksBundle:
  implicit val json: Codec[TracksBundle] = deriveCodec[TracksBundle]

case class InsertedPoint(point: TrackPointId, track: JoinedTrack):
  def strip(formatter: TimeFormatter) =
    InsertedTrackPoint(point, track.strip(formatter))

case class JoinedDevice(id: DeviceId, username: Username)

case class GPSInsertedPoint(point: GPSPointId, from: JoinedBoat)

case class TrackNumbers(
  track: TrackId,
  start: Option[Instant],
  end: Option[Instant],
  topSpeed: Option[SpeedM]
)

case class TrackMeta(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  trackCanonical: TrackCanonical,
  comments: Option[String],
  trackAdded: Instant,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  points: Int,
  distance: DistanceM,
  boat: DeviceId,
  boatName: BoatName,
  boatToken: BoatToken,
  userId: UserId,
  username: Username,
  email: Option[Email]
) extends UserDevice:
  override def deviceName = boatName

  def short = TrackMetaShort(track, trackName, boat, boatName, username)

sealed trait InputEvent

case object EmptyEvent extends InputEvent
case class BoatEvent(message: Json, from: TrackMeta) extends InputEvent
case class DeviceEvent(message: Json, from: IdentifiedDeviceMeta) extends InputEvent

case class BoatJsonError(error: DecodingFailure, boat: BoatEvent)
case class DeviceJsonError(error: DecodingFailure, boat: DeviceEvent)

object SingleErrors:
  def forJWT(error: JWTError): SingleError =
    SingleError(error.message, error.key)

object BoatNames:
  val Key = "boatName"
  val BoatKey = "boat"

  def random() = BoatName(Utils.randomString(6))

case class SingleToken(token: PushToken)

object SingleToken:
  implicit val json: Codec[SingleToken] = deriveCodec[SingleToken]

case class PushPayload(token: PushToken, device: MobileDevice)

object PushPayload:
  implicit val json: Codec[PushPayload] = deriveCodec[PushPayload]

object TrackNames:
  def random() = TrackName(Utils.randomString(6))

  def apply(title: TrackTitle): TrackName =
    TrackName(Utils.normalize(title.title).take(50))

object TrackTitles:
  val MaxLength = 191

object UserUtils:
  def random() = Username(Utils.randomString(6))

object BoatTokens:
  def random() = BoatToken(Utils.randomString(8))

case class BoatInput(name: BoatName, token: BoatToken, owner: UserId)

case class BoatResponse(boat: Boat)

object BoatResponse:
  implicit val json: Codec[BoatResponse] = deriveCodec[BoatResponse]

case class JoinedBoat(
  device: DeviceId,
  boatName: BoatName,
  boatToken: BoatToken,
  userId: UserId,
  username: Username,
  email: Option[Email],
  language: Language
) extends IdentifiedDeviceMeta
  with UserDevice:
  override def user = username
  override def boat = boatName
  override def deviceName = boatName
  def strip = DeviceRef(device, boatName, username)

case class TrackInput(
  name: TrackName,
  boat: DeviceId,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  points: Int,
  distance: DistanceM,
  canonical: TrackCanonical
)

object TrackInput:
  def empty(name: TrackName, boat: DeviceId): TrackInput =
    TrackInput(
      name,
      boat,
      None,
      None,
      0,
      DistanceM.zero,
      TrackCanonical.fromName(name)
    )

case class SentenceKey(id: Long) extends AnyVal with WrappedId

object SentenceKey extends BoatIdCompanion[SentenceKey]

case class GPSSentenceKey(id: Long) extends AnyVal with WrappedId

object GPSSentenceKey extends BoatIdCompanion[GPSSentenceKey]

case class GPSKeyedSentence(key: GPSSentenceKey, sentence: RawSentence, from: DeviceId)

case class GPSSentenceInput(sentence: RawSentence, boat: DeviceId)

case class SentenceInput(sentence: RawSentence, track: TrackId)

case class KeyedSentence(key: SentenceKey, sentence: RawSentence, from: TrackMetaShort)

case class SentenceRow(id: SentenceKey, sentence: RawSentence, track: TrackId, added: Instant):
  def timed(formatter: TimeFormatter) =
    TimedSentence(id, sentence, track, added, formatter.timing(added))

object SentenceRow:
  implicit val json: Codec[SentenceRow] = deriveCodec[SentenceRow]

case class GPSSentenceRow(
  id: GPSSentenceKey,
  sentence: RawSentence,
  device: DeviceId,
  added: Instant
)

object GPSSentenceRow:
  implicit val json: Codec[GPSSentenceRow] = deriveCodec[GPSSentenceRow]

case class GPSPointInput(
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  satellites: Int,
  fix: GPSFix,
  gpsTime: Instant,
  diff: DistanceM,
  device: DeviceId,
  pointIndex: Int
)

object GPSPointInput:
  def forCoord(c: GPSCoord, idx: Int, diff: DistanceM) =
    GPSPointInput(
      c.lng,
      c.lat,
      c.coord,
      c.satellites,
      c.fix,
      c.gpsTime,
      diff,
      c.device,
      idx
    )

case class GPSPointRow(
  id: GPSPointId,
  longitude: Longitude,
  latitude: Latitude,
  coord: Coord,
  satellites: Int,
  fix: GPSFix,
  pointIndex: Int,
  gpsTime: Instant,
  diff: DistanceM,
  device: DeviceId,
  added: Instant
)

case class TimedSentence(
  id: SentenceKey,
  sentence: RawSentence,
  track: TrackId,
  added: Instant,
  time: Timing
)

object TimedSentence:
  implicit val json: Codec[TimedSentence] = deriveCodec[TimedSentence]

case class Sentences(sentences: Seq[RawSentence])

object Sentences:
  val Key = SentencesEvent.Key
  implicit val json: Codec[Sentences] = deriveCodec[Sentences]

case class TrackPointInput(
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depth: DistanceM,
  depthOffset: DistanceM,
  boatTime: Instant,
  track: TrackId,
  trackIndex: Int,
  diff: DistanceM
)

object TrackPointInput:
  def forCoord(c: FullCoord, trackIndex: Int, diff: DistanceM): TrackPointInput =
    TrackPointInput(
      c.lng,
      c.lat,
      c.coord,
      c.boatSpeed,
      c.waterTemp,
      c.depth,
      c.depthOffset,
      c.boatTime,
      c.from.track,
      trackIndex,
      diff
    )

case class SentenceCoord2(
  id: TrackPointId,
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depth: DistanceM,
  depthOffset: DistanceM,
  boatTime: Instant,
  date: DateVal,
  track: TrackId,
  added: Instant,
  sentenceId: SentenceKey,
  sentenceRaw: RawSentence,
  sentenceAdded: Instant
):
  def c = CombinedCoord(
    id,
    lon,
    lat,
    coord,
    boatSpeed,
    waterTemp,
    depth,
    depthOffset,
    boatTime,
    date,
    track,
    added
  )
  def s = SentenceRow(sentenceId, sentenceRaw, track, sentenceAdded)

case class CombinedCoord(
  id: TrackPointId,
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depth: DistanceM,
  depthOffset: DistanceM,
  boatTime: Instant,
  date: DateVal,
  track: TrackId,
  added: Instant
):

  def toFull(sentences: Seq[SentenceRow], formatter: TimeFormatter): CombinedFullCoord =
    CombinedFullCoord(
      id,
      lon,
      lat,
      coord,
      boatSpeed,
      waterTemp,
      depth,
      depthOffset,
      boatTime,
      date,
      track,
      added,
      sentences.map(_.timed(formatter)),
      formatter.timing(boatTime)
    )

  def timed(formatter: TimeFormatter): TimedCoord =
    val instant = boatTime
    TimedCoord(
      id,
      coord,
      formatter.formatDateTime(instant),
      instant.toEpochMilli,
      formatter.formatTime(instant),
      boatSpeed,
      waterTemp,
      depth,
      formatter.timing(instant)
    )

case class TrackInfo(coords: Seq[CombinedCoord], topPoint: Option[CombinedCoord])

case class CombinedFullCoord(
  id: TrackPointId,
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depthMeters: DistanceM,
  depthOffsetMeters: DistanceM,
  boatTime: Instant,
  date: DateVal,
  track: TrackId,
  added: Instant,
  sentences: Seq[TimedSentence],
  time: Timing
)

object CombinedFullCoord:
  val modern: Codec[CombinedFullCoord] = deriveCodec[CombinedFullCoord]
  implicit val json: Codec[CombinedFullCoord] = Codec.from(
    modern,
    (c: CombinedFullCoord) =>
      modern(c).deepMerge(
        Json.obj(
          "depth" -> c.depthMeters.toMillis.toLong.asJson,
          "depthOffset" -> c.depthOffsetMeters.toMillis.toLong.asJson
        )
      )
  )

case class FullTrack(track: TrackRef, coords: Seq[CombinedFullCoord]):
  def name = track.trackName

object FullTrack:
  implicit val json: Codec[FullTrack] = deriveCodec[FullTrack]

case class TrackPointRow(
  id: TrackPointId,
  longitude: Longitude,
  latitude: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  waterTemp: Temperature,
  depthm: DistanceM,
  depthOffsetm: DistanceM,
  boatTime: Instant,
  track: TrackId,
  trackIndex: Int,
  diff: DistanceM,
  added: Instant
):
  def depth = depthm
  def depthOffset = depthOffsetm
  def dateTimeUtc = boatTime.atOffset(ZoneOffset.UTC)
  def time = LocalTime.from(dateTimeUtc)
  def date = LocalDate.from(dateTimeUtc)

  def combined(date: DateVal) =
    CombinedCoord(
      id,
      longitude,
      latitude,
      coord,
      boatSpeed,
      waterTemp,
      depth,
      depthOffset,
      boatTime,
      date,
      track,
      added
    )

case class SentencePointLink(sentence: SentenceKey, point: TrackPointId)

case class GPSSentencePointLink(sentence: GPSSentenceKey, point: GPSPointId)

case class TrackPoint(coord: Coord, time: Instant, waterTemp: Temperature, wind: Double)

object TrackPoint:
  implicit val json: Codec[TrackPoint] = deriveCodec[TrackPoint]

case class Track(id: TrackId, name: TrackName, points: Seq[TrackPoint])

object Track:
  implicit val json: Codec[Track] = deriveCodec[Track]

case class RouteId(id: Long) extends WrappedId

object RouteId extends IdCompanion[RouteId]

case class Route(id: RouteId, name: String, points: Seq[Coord])

object Route:
  implicit val json: Codec[Route] = deriveCodec[Route]

abstract class BoatStringCompanion[T <: WrappedString] extends StringCompanion[T]:
  val db: Meta[T] = Meta[String].timap[T](apply)(_.value)

abstract class BoatIdCompanion[T <: WrappedId] extends IdCompanion[T]:
  val db: Meta[T] = Meta[Long].timap[T](apply)(_.id)
