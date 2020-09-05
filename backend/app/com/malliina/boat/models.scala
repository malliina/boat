package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.BoatPrimitives.durationFormat
import com.malliina.boat.parsing.{FullCoord, GPSCoord, GPSFix}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.play.auth.JWTError
import com.malliina.values._
import play.api.data.{Forms, Mapping}
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.PathBindable

import scala.concurrent.duration.FiniteDuration

case class CSRFToken(token: String) extends AnyVal

case class Languages(finnish: Lang, swedish: Lang, english: Lang)

object Languages {
  implicit val json = Json.format[Languages]
}

case class AisConf(vessel: String, trail: String, vesselIcon: String)

object AisConf {
  implicit val json = Json.format[AisConf]
}

case class Layers(
  marks: Seq[String],
  fairways: Seq[String],
  fairwayAreas: Seq[String],
  limits: Seq[String],
  ais: AisConf
)

object Layers {
  implicit val json = Json.format[Layers]
  import MapboxStyles._

  val default = Layers(
    marksLayers,
    fairwayLayers,
    Seq(FairwayAreaId),
    Seq(LimitLayerId),
    AisConf(AisVesselLayer, AisTrailLayer, AisVesselIcon)
  )
}

case class ClientConf(map: MapConf, languages: Languages, layers: Layers)

object ClientConf {
  implicit val json = Json.format[ClientConf]
  val default =
    ClientConf(MapConf.active, Languages(Lang.fi, Lang.se, Lang.en), Layers.default)
}

/** Alternative to LocalDate because according to its Javadoc reference equality and other
  * operations may have unpredictable results whereas this class has predictable structural equality
  * properties.
  */
case class DateVal(year: YearVal, month: MonthVal, day: DayVal) {
  def toLocalDate = LocalDate.of(year.year, month.month, day.day)
  def plusDays(days: Int) = DateVal(toLocalDate.plusDays(days))
  def plusMonths(months: Int) = DateVal(toLocalDate.plusMonths(months))
  def plusYears(years: Int) = DateVal(toLocalDate.plusYears(years))
  def iso8601 = toLocalDate.toString
  override def toString = iso8601
}

object DateVal {
  implicit val json = Format[DateVal](
    Reads(_.validate[LocalDate].map(d => DateVal(d))),
    Writes(dv => Json.toJson(dv.toLocalDate))
  )
  def now(): DateVal = apply(LocalDate.now())
  def apply(date: LocalDate): DateVal =
    DateVal(
      YearVal(date.getYear),
      MonthVal(date.getMonthValue),
      DayVal(date.getDayOfMonth)
    )
}

case class UserToken(token: String) extends AnyVal with WrappedString {
  override def value = token
}

object UserToken extends StringCompanion[UserToken] {
  val minLength = 3

  override def build(in: String): Either[ErrorMessage, UserToken] =
    if (in.length >= minLength) Right(UserToken(in))
    else
      Left(
        ErrorMessage(
          s"Too short token. Minimum length: $minLength, was: ${in.length}."
        )
      )

  def random(): UserToken = UserToken(Utils.randomString(length = 8))
}

case class AppMeta(name: String, version: String, gitHash: String, mapboxVersion: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]
  val default =
    AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash, BuildInfo.mapboxVersion)
}

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
) extends TrackLike {
  def boatId = boat.device
  def language = boat.language
  def user = boat.user
  override def boatName = boat.boatName
  override def username = boat.username

  val startOrNow = start.getOrElse(Instant.now())
  val endOrNow = end.getOrElse(Instant.now())

  /**
    * @return a Scala.js -compatible representation of this track
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
}

case class Stats(
  label: String,
  from: DateVal,
  to: DateVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long
)

object Stats {
  implicit val json = Json.format[Stats]
}

case class MonthlyStats(
  label: String,
  year: YearVal,
  month: MonthVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long
)

object MonthlyStats {
  implicit val json = Json.format[MonthlyStats]
}

case class YearlyStats(
  label: String,
  year: YearVal,
  trackCount: Long,
  distance: DistanceM,
  duration: FiniteDuration,
  days: Long,
  monthly: Seq[MonthlyStats]
)

object YearlyStats {
  implicit val json = Json.format[YearlyStats]
}

case class StatsResponse(daily: Seq[Stats], yearly: Seq[YearlyStats], allTime: Stats)

object StatsResponse {
  implicit val json = Json.format[StatsResponse]
}

case class TracksBundle(tracks: Seq[TrackRef], stats: StatsResponse)

object TracksBundle {
  implicit val json = Json.format[TracksBundle]
}

case class InsertedPoint(point: TrackPointId, track: JoinedTrack) {
  def strip(formatter: TimeFormatter) =
    InsertedTrackPoint(point, track.strip(formatter))
}

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
) extends UserDevice {
  override def deviceName = boatName

  def short = TrackMetaShort(track, trackName, boat, boatName, username)
}

case class BoatEvent(message: JsValue, from: TrackMeta)
case class DeviceEvent(message: JsValue, from: IdentifiedDeviceMeta)

case class BoatJsonError(error: JsError, boat: BoatEvent)
case class DeviceJsonError(error: JsError, boat: DeviceEvent)

case class SingleError(message: ErrorMessage, key: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def apply(message: String, key: String): SingleError = SingleError(ErrorMessage(message), key)

  def input(message: String) = apply(ErrorMessage(message), "input")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message, error.key)
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
  implicit val html: Writeable[Errors] =
    Writeable.writeableOf_JsValue.map[Errors](e => Json.toJson(e))

  def apply(error: SingleError): Errors = Errors(Seq(error))
  def apply(message: String): Errors = apply(message, "generic")
  def apply(message: String, key: String): Errors = apply(SingleError(message, key))
}

object BoatNames {
  val Key = "boatName"
  val BoatKey = "boat"
  val mapping: Mapping[BoatName] =
    Forms.nonEmptyText.transform(s => BoatName(s), b => b.name)

  def random() = BoatName(Utils.randomString(6))
}

object Emails {
  val Key = "email"

  val mapping: Mapping[Email] =
    Forms.nonEmptyText.transform(s => Email(s), b => b.email)

}

case class SingleToken(token: PushToken)

object SingleToken {
  implicit val json = Json.format[SingleToken]
}

case class PushPayload(token: PushToken, device: MobileDevice)

object PushPayload {
  implicit val json = Json.format[PushPayload]
}

object TrackNames {
  def random() = TrackName(Utils.randomString(6))

  def apply(title: TrackTitle): TrackName =
    TrackName(Utils.normalize(title.title).take(50))
}

object TrackTitles {
  val MaxLength = 191
  val mapping: Mapping[TrackTitle] = Forms.nonEmptyText
    .verifying(_.length <= TrackTitle.MaxLength)
    .transform(s => TrackTitle(s), t => t.title)
}

object BoatTokens {
  def random() = BoatToken(Utils.randomString(8))
}

// Bindables._ is imported to the routes file, see build.sbt
object Bindables {
  implicit val trackName: PathBindable[TrackName] =
    PathBindable.bindableString
      .transform[TrackName](s => TrackName(s), t => t.name)

  implicit val trackCanonical: PathBindable[TrackCanonical] =
    PathBindable.bindableString
      .transform[TrackCanonical](s => TrackCanonical(s), t => t.name)

  implicit val boatName: PathBindable[BoatName] =
    PathBindable.bindableString
      .transform[BoatName](s => BoatName(s), t => t.name)

  implicit val boatId: PathBindable[DeviceId] =
    PathBindable.bindableLong.transform[DeviceId](s => DeviceId(s), id => id.id)

  implicit val trackId: PathBindable[TrackId] =
    PathBindable.bindableLong.transform[TrackId](TrackId.apply, _.id)
}

case class BoatInput(name: BoatName, token: BoatToken, owner: UserId)

case class BoatResponse(boat: Boat)

object BoatResponse {
  implicit val json = Json.format[BoatResponse]
}

case class JoinedBoat(
  device: DeviceId,
  boatName: BoatName,
  boatToken: BoatToken,
  userId: UserId,
  username: Username,
  email: Option[Email],
  language: Language
) extends IdentifiedDeviceMeta
  with UserDevice {
  override def user = username
  override def boat = boatName
  override def deviceName = boatName
  def strip = DeviceRef(device, boatName, username)
}

case class TrackInput(
  name: TrackName,
  boat: DeviceId,
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  points: Int,
  distance: DistanceM,
  canonical: TrackCanonical
)

object TrackInput {
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
}

case class SentenceKey(id: Long) extends AnyVal with WrappedId

object SentenceKey extends IdCompanion[SentenceKey]

case class GPSSentenceKey(id: Long) extends AnyVal with WrappedId

object GPSSentenceKey extends IdCompanion[GPSSentenceKey]

case class GPSKeyedSentence(key: GPSSentenceKey, sentence: RawSentence, from: DeviceId)

case class GPSSentenceInput(sentence: RawSentence, boat: DeviceId)

case class SentenceInput(sentence: RawSentence, track: TrackId)

case class KeyedSentence(key: SentenceKey, sentence: RawSentence, from: TrackMetaShort)

case class SentenceRow(id: SentenceKey, sentence: RawSentence, track: TrackId, added: Instant) {
  def timed(formatter: TimeFormatter) =
    TimedSentence(id, sentence, track, added, formatter.timing(added))
}

object SentenceRow {
  implicit val json = Json.format[SentenceRow]
}

case class GPSSentenceRow(
  id: GPSSentenceKey,
  sentence: RawSentence,
  device: DeviceId,
  added: Instant
)

object GPSSentenceRow {
  implicit val json = Json.format[GPSSentenceRow]
}

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

object GPSPointInput {
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
}

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

object TimedSentence {
  implicit val json = Json.format[TimedSentence]
}

case class Sentences(sentences: Seq[RawSentence])

object Sentences {
  val Key = SentencesEvent.Key
  implicit val json = Json.format[Sentences]
}

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

object TrackPointInput {
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
}

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
) {
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
}

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
) {

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

  def timed(formatter: TimeFormatter): TimedCoord = {
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
  }
}

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

object CombinedFullCoord {
  val modern = Json.format[CombinedFullCoord]
  implicit val json = Format[CombinedFullCoord](
    modern,
    Writes(c =>
      modern.writes(c) ++ Json.obj(
        "depth" -> c.depthMeters.toMillis.toLong,
        "depthOffset" -> c.depthOffsetMeters.toMillis.toLong
      )
    )
  )
}

case class FullTrack(track: TrackRef, coords: Seq[CombinedFullCoord]) {
  def name = track.trackName
}

object FullTrack {
  implicit val json = Json.format[FullTrack]
}

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
) {
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
}

case class SentencePointLink(sentence: SentenceKey, point: TrackPointId)

case class GPSSentencePointLink(sentence: GPSSentenceKey, point: GPSPointId)

case class TrackPoint(coord: Coord, time: Instant, waterTemp: Temperature, wind: Double)

object TrackPoint {
  implicit val json = Json.format[TrackPoint]
}

case class Track(id: TrackId, name: TrackName, points: Seq[TrackPoint])

object Track {
  implicit val json = Json.format[Track]
}

case class RouteId(id: Long) extends WrappedId

object RouteId extends IdCompanion[RouteId]

case class Route(id: RouteId, name: String, points: Seq[Coord])

object Route {
  implicit val json = Json.format[Route]
}
