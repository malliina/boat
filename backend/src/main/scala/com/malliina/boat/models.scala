package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import com.malliina.boat.BoatPrimitives.durationFormat
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.*
import doobie.Meta
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.EncoderOps

import scala.concurrent.duration.FiniteDuration

opaque type CarUpdateId = Long
object CarUpdateId extends JsonCompanion[Long, CarUpdateId]:
  override def apply(raw: Long): CarUpdateId = raw
  override def write(t: CarUpdateId): Long = t
extension (id: CarUpdateId) def long: Long = id

case class LocationUpdate(
  longitude: Longitude,
  latitude: Latitude,
  altitudeMeters: Option[DistanceM],
  accuracyMeters: Option[DistanceM],
  bearing: Option[Degrees],
  bearingAccuracyDegrees: Option[Degrees],
  speed: Option[SpeedM],
  batteryLevel: Option[Energy],
  batteryCapacity: Option[Energy],
  rangeRemaining: Option[DistanceM],
  outsideTemperature: Option[Temperature],
  nightMode: Option[Boolean],
  date: OffsetDateTime
):
  val coord = Coord(longitude, latitude)

object LocationUpdate:
  implicit val speedReader: Codec[SpeedM] = Codec.from(
    Decoder.decodeDouble.map(mps => SpeedM(mps)),
    Encoder.encodeDouble.contramap(_.toMps)
  )
  implicit val json: Codec[LocationUpdate] = deriveCodec[LocationUpdate]

case class LocationUpdates(updates: List[LocationUpdate], carId: DeviceId) derives Codec.AsObject

case class Languages(finnish: Lang, swedish: Lang, english: Lang) derives Codec.AsObject

case class AisConf(vessel: String, trail: String, vesselIcon: String) derives Codec.AsObject

case class Layers(
  marks: Seq[String],
  fairways: Seq[String],
  fairwayAreas: Seq[String],
  depthAreas: Seq[String],
  limits: Seq[String],
  ais: AisConf
) derives Codec.AsObject

object Layers:
  import MapboxStyles.*

  val default = Layers(
    marksLayers,
    fairwayLayers,
    FairwayAreaLayers,
    DepthAreaLayers,
    LimitLayerIds,
    AisConf(AisVesselLayer, AisTrailLayer, AisVesselIcon)
  )

case class ClientConf(map: MapConf, languages: Languages, layers: Layers) derives Codec.AsObject

object ClientConf:
  val default = withMap(MapConf.active)
  val old = withMap(MapConf.old)

  private def withMap(map: MapConf) =
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

case class AppMeta(name: String, version: String, gitHash: String) derives Codec.AsObject

object AppMeta:
  val default =
    AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)

case class CarRow(
  coord: Coord,
  diff: DistanceM,
  speed: Option[SpeedM],
  altitude: Option[DistanceM],
  batteryLevel: Option[Energy],
  batteryCapacity: Option[Energy],
  rangeRemaining: Option[DistanceM],
  outsideTemperature: Option[Temperature],
  nightMode: Option[Boolean],
  carTime: Instant,
  added: Instant,
  car: CarInfo
)

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
  avgOutsideTemp: Option[Temperature],
  distance: DistanceM,
  start: Option[Instant],
  startDate: DateVal,
  startMonth: MonthVal,
  startYear: YearVal,
  end: Option[Instant],
  duration: FiniteDuration,
  topSpeed: Option[SpeedM],
  tip: CombinedCoord,
  boat: JoinedSource
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
    boat.sourceType,
    username,
    points,
    duration,
    distance,
    topSpeed,
    avgSpeed,
    avgWaterTemp,
    avgOutsideTemp,
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
) derives Codec.AsObject

case class MonthlyStats(
  label: String,
  year: YearVal,
  month: MonthVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long
) derives Codec.AsObject

case class YearlyStats(
  label: String,
  year: YearVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long,
  monthly: Seq[MonthlyStats]
) derives Codec.AsObject

case class StatsResponse(daily: Seq[Stats], yearly: Seq[YearlyStats], allTime: Stats)
  derives Codec.AsObject

case class TracksBundle(tracks: Seq[TrackRef], stats: StatsResponse) derives Codec.AsObject

case class InsertedPoint(point: TrackPointId, track: JoinedTrack):
  def strip(formatter: TimeFormatter) =
    InsertedTrackPoint(point, track.strip(formatter))

case class TrackMeta(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  trackCanonical: TrackCanonical,
  comments: Option[String],
  trackAdded: Instant,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  avgOutsideTemp: Option[Temperature],
  points: Int,
  distance: DistanceM,
  boat: DeviceId,
  boatName: BoatName,
  sourceType: SourceType,
  boatToken: BoatToken,
  userId: UserId,
  username: Username,
  email: Option[Email]
) extends UserDevice:
  override def deviceName = boatName
  override def device = boat
  def short = TrackMetaShort(track, trackName, boat, boatName, username)

case class JoinResult(track: TrackMeta, isResumed: Boolean)

sealed trait InputEvent

case object EmptyEvent extends InputEvent
case class BoatEvent(message: Json, from: TrackMeta) extends InputEvent
case class DeviceEvent(message: Json, from: IdentifiedDeviceMeta) extends InputEvent
case class CarEvent(body: LocationUpdates, meta: DeviceMeta) extends InputEvent

case class BoatJsonError(error: DecodingFailure, boat: BoatEvent)
case class DeviceJsonError(error: DecodingFailure, boat: DeviceEvent)

object BoatNames:
  val Key = "boatName"
  val BoatKey = "boat"

  def random() = BoatName(Utils.randomString(6))

case class PushPayload(token: PushToken, device: MobileDevice) derives Codec.AsObject

case class DisablePush(token: PushToken) derives Codec.AsObject

object TrackNames:
  def random() = TrackName(Utils.randomString(6))

object UserUtils:
  def random() = Username(Utils.randomString(6))

object BoatTokens:
  def random() = BoatToken(Utils.randomString(8))

case class BoatResponse(boat: Boat) derives Codec.AsObject

case class JoinedSource(
  device: DeviceId,
  boatName: BoatName,
  sourceType: SourceType,
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

case class VesselRowId(id: Long) extends AnyVal with WrappedId
object VesselRowId extends BoatIdCompanion[VesselRowId]

case class AisUpdateId(id: Long) extends AnyVal with WrappedId
object AisUpdateId extends BoatIdCompanion[AisUpdateId]

case class SentenceKey(id: Long) extends AnyVal with WrappedId
object SentenceKey extends BoatIdCompanion[SentenceKey]

case class GPSSentenceKey(id: Long) extends AnyVal with WrappedId
object GPSSentenceKey extends BoatIdCompanion[GPSSentenceKey]

case class GPSKeyedSentence(key: GPSSentenceKey, sentence: RawSentence, from: DeviceId)

case class KeyedSentence(key: SentenceKey, sentence: RawSentence, from: TrackMetaShort)

case class SentenceRow(id: SentenceKey, sentence: RawSentence, track: TrackId, added: Instant)
  derives Codec.AsObject:
  def timed(formatter: TimeFormatter) =
    TimedSentence(id, sentence, track, added, formatter.timing(added))

case class TimedSentence(
  id: SentenceKey,
  sentence: RawSentence,
  track: TrackId,
  added: Instant,
  time: Timing
) derives Codec.AsObject

case class Sentences(sentences: Seq[RawSentence]) derives Codec.AsObject

object Sentences:
  val Key = SentencesEvent.Key

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

case class SentenceCoord2(
  id: TrackPointId,
  lon: Longitude,
  lat: Latitude,
  coord: Coord,
  boatSpeed: SpeedM,
  altitude: Option[DistanceM],
  outsideTemp: Option[Temperature],
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
    Option(boatSpeed),
    altitude,
    outsideTemp,
    Option(waterTemp),
    Option(depth),
    Option(depthOffset),
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
  speed: Option[SpeedM],
  altitude: Option[DistanceM],
  outsideTemp: Option[Temperature],
  waterTemp: Option[Temperature],
  depth: Option[DistanceM],
  depthOffset: Option[DistanceM],
  sourceTime: Instant,
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
      speed.getOrElse(SpeedM.zero),
      waterTemp.getOrElse(Temperature.zeroCelsius),
      depth.getOrElse(DistanceM.zero),
      depthOffset.getOrElse(DistanceM.zero),
      sourceTime,
      date,
      track,
      added,
      sentences.map(_.timed(formatter)),
      formatter.timing(sourceTime)
    )

  // TODO Fix the getOrElses, they're lies
  def timed(formatter: TimeFormatter): TimedCoord =
    val instant = sourceTime
    TimedCoord(
      id,
      coord,
      formatter.formatDateTime(instant),
      instant.toEpochMilli,
      formatter.formatTime(instant),
      speed.getOrElse(SpeedM.zero),
      altitude,
      outsideTemp,
      waterTemp.getOrElse(Temperature.zeroCelsius),
      depth.getOrElse(DistanceM.zero),
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
  private val modern: Codec[CombinedFullCoord] = deriveCodec[CombinedFullCoord]
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

case class FullTrack(track: TrackRef, coords: Seq[CombinedFullCoord]) derives Codec.AsObject:
  def name = track.trackName

case class TrackPointRow(
  id: TrackPointId,
  longitude: Longitude,
  latitude: Latitude,
  coord: Coord,
  speed: Option[SpeedM],
  altitude: Option[DistanceM],
  outsideTemp: Option[Temperature],
  waterTemp: Option[Temperature],
  depthm: Option[DistanceM],
  depthOffsetm: Option[DistanceM],
  sourceTime: Instant,
  track: TrackId,
  trackIndex: Int,
  diff: DistanceM,
  added: Instant
):
  def depth = depthm
  def depthOffset = depthOffsetm
  def dateTimeUtc = sourceTime.atOffset(ZoneOffset.UTC)
  def time = LocalTime.from(dateTimeUtc)
  def date = LocalDate.from(dateTimeUtc)

case class TrackPoint(coord: Coord, time: Instant, waterTemp: Temperature, wind: Double)
  derives Codec.AsObject

case class Track(id: TrackId, name: TrackName, points: Seq[TrackPoint]) derives Codec.AsObject

case class RouteId(id: Long) extends WrappedId

object RouteId extends IdCompanion[RouteId]

case class Route(id: RouteId, name: String, points: Seq[Coord]) derives Codec.AsObject

abstract class BoatStringCompanion[T <: WrappedString] extends StringCompanion[T]:
  val db: Meta[T] = Meta[String].timap[T](apply)(_.value)

abstract class BoatIdCompanion[T <: WrappedId] extends IdCompanion[T]:
  val db: Meta[T] = Meta[Long].timap[T](apply)(_.id)
