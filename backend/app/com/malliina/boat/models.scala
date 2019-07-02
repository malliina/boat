package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.play.auth.JWTError
import com.malliina.values._
import play.api.data.{Forms, Mapping}
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.PathBindable

import scala.concurrent.duration.DurationLong

case class Languages(finnish: Lang, swedish: Lang, english: Lang)

object Languages {
  implicit val json = Json.format[Languages]
}

case class AisConf(vessel: String, trail: String, vesselIcon: String)

object AisConf {
  implicit val json = Json.format[AisConf]
}

case class Layers(marks: Seq[String],
                  fairways: Seq[String],
                  fairwayAreas: Seq[String],
                  limits: Seq[String],
                  ais: AisConf)

object Layers {
  implicit val json = Json.format[Layers]
  import MapboxStyles._

  val default = Layers(marksLayers,
                       fairwayLayers,
                       Seq(FairwayAreaId),
                       Seq(LimitLayerId),
                       AisConf(AisVesselLayer, AisTrailLayer, AisVesselIcon))
}

case class ClientConf(languages: Languages, layers: Layers)

object ClientConf {
  implicit val json = Json.format[ClientConf]
  val default = ClientConf(Languages(Lang.fi, Lang.se, Lang.en), Layers.default)
}

case class UserToken(token: String) extends WrappedString {
  override def value = token
}

object UserToken extends StringCompanion[UserToken] {
  val minLength = 3

  override def build(in: String): Either[ErrorMessage, UserToken] =
    if (in.length >= minLength) Right(UserToken(in))
    else Left(ErrorMessage(s"Too short token. Minimum length: $minLength, was: ${in.length}."))

  def random(): UserToken = UserToken(Utils.randomString(length = 8))
}

case class AppMeta(name: String, version: String, gitHash: String, mapboxVersion: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]
//  val default =
//    AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash, BuildInfo.mapboxVersion)
  val default =
    AppMeta("test", "0.0.1", "abcd123", "0.53.1")
}

case class JoinedTrack(track: TrackId,
                       trackName: TrackName,
                       trackTitle: Option[TrackTitle],
                       canonical: TrackCanonical,
                       comments: Option[String],
                       trackAdded: Instant,
                       boat: BoatId,
                       boatName: BoatName,
                       boatToken: BoatToken,
                       user: UserId,
                       username: Username,
                       email: Option[Email],
                       language: Language,
                       points: Int,
                       start: Option[java.sql.Timestamp],
                       end: Option[java.sql.Timestamp],
                       topSpeed: Option[SpeedM],
                       avgSpeed: Option[SpeedM],
                       avgWaterTemp: Option[Temperature],
                       distance: DistanceM,
                       topPoint: CombinedCoord)
    extends TrackLike {
  val startOrNow = start.map(_.toInstant).getOrElse(Instant.now())
  val endOrNow = end.map(_.toInstant).getOrElse(Instant.now())
  val duration = (endOrNow.toEpochMilli - startOrNow.toEpochMilli).millis

  /**
    * @return a Scala.js -compatible representation of this track
    */
  def strip(formatter: TimeFormatter) = TrackRef(
    track,
    trackName,
    trackTitle,
    canonical,
    comments,
    boat,
    boatName,
    username,
    points,
    duration,
    distance,
    topSpeed,
    avgSpeed,
    avgWaterTemp,
    topPoint.timed(formatter),
    formatter.times(startOrNow, endOrNow)
  )
}

case class InsertedPoint(point: TrackPointId, track: JoinedTrack) {
  def strip(formatter: TimeFormatter) = InsertedTrackPoint(point, track.strip(formatter))
}

case class TrackNumbers(track: TrackId,
                        start: Option[Instant],
                        end: Option[Instant],
                        topSpeed: Option[SpeedM])

case class TrackMeta(track: TrackId,
                     trackName: TrackName,
                     trackTitle: Option[TrackTitle],
                     trackCanonical: TrackCanonical,
                     comments: Option[String],
                     trackAdded: Instant,
                     avgSpeed: Option[SpeedM],
                     avgWaterTemp: Option[Temperature],
                     points: Int,
                     distance: DistanceM,
                     boat: BoatId,
                     boatName: BoatName,
                     boatToken: BoatToken,
                     user: UserId,
                     username: Username,
                     email: Option[Email]) {
  def short = TrackMetaShort(track, trackName, boat, boatName, username)
}

case class BoatEvent(message: JsValue, from: TrackMeta)

case class BoatJsonError(error: JsError, boat: BoatEvent)

case class SingleError(message: String, key: String)

object SingleError {
  implicit val json = Json.format[SingleError]

  def input(message: String) = apply(message, "input")

  def forJWT(error: JWTError): SingleError =
    SingleError(error.message, error.key)
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
  implicit val html: Writeable[Errors] =
    Writeable.writeableOf_JsValue.map[Errors](e => Json.toJson(e))

  def apply(error: SingleError): Errors = Errors(Seq(error))

  def apply(message: String): Errors = apply(SingleError(message, "generic"))
}

object BoatNames {
  val Key = "boatName"
  val mapping: Mapping[BoatName] = Forms.nonEmptyText.transform(s => BoatName(s), b => b.name)

  def random() = BoatName(Utils.randomString(6))
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

  def apply(title: TrackTitle): TrackName = TrackName(Utils.normalize(title.title).take(50))
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
    PathBindable.bindableString.transform[TrackName](s => TrackName(s), t => t.name)

  implicit val trackCanonical: PathBindable[TrackCanonical] =
    PathBindable.bindableString.transform[TrackCanonical](s => TrackCanonical(s), t => t.name)

  implicit val boatName: PathBindable[BoatName] =
    PathBindable.bindableString.transform[BoatName](s => BoatName(s), t => t.name)

  implicit val boatId: PathBindable[BoatId] =
    PathBindable.bindableLong.transform[BoatId](s => BoatId(s), id => id.id)

  implicit val trackId: PathBindable[TrackId] =
    PathBindable.bindableLong.transform[TrackId](TrackId.apply, _.id)
}

case class BoatInput(name: BoatName, token: BoatToken, owner: UserId)

case class BoatRow(id: BoatId, name: BoatName, token: BoatToken, owner: UserId, added: Instant) {
  def toBoat = Boat(id, name, token, added.toEpochMilli)
}

case class BoatResponse(boat: Boat)

object BoatResponse {
  implicit val json = Json.format[BoatResponse]
}

case class JoinedBoat(boat: BoatId,
                      boatName: BoatName,
                      boatToken: BoatToken,
                      user: UserId,
                      username: Username,
                      email: Option[Email],
                      language: Language)

case class TrackInput(name: TrackName,
                      boat: BoatId,
                      avgSpeed: Option[SpeedM],
                      avgWaterTemp: Option[Temperature],
                      points: Int,
                      distance: DistanceM,
                      canonical: TrackCanonical)

object TrackInput {
  def empty(name: TrackName, boat: BoatId): TrackInput =
    TrackInput(name, boat, None, None, 0, DistanceM.zero, TrackCanonical(name))
}

case class TrackRow(id: TrackId,
                    name: TrackName,
                    boat: BoatId,
                    avgSpeed: Option[SpeedM],
                    avgWaterTemp: Option[Temperature],
                    points: Int,
                    distance: DistanceM,
                    title: Option[TrackTitle],
                    canonical: TrackCanonical,
                    comments: Option[String],
                    added: Instant)

case class SentenceKey(id: Long) extends WrappedId

object SentenceKey extends IdCompanion[SentenceKey]

case class SentenceInput(sentence: RawSentence, track: TrackId)

case class KeyedSentence(key: SentenceKey, sentence: RawSentence, from: TrackMetaShort)

case class SentenceRow(id: SentenceKey, sentence: RawSentence, track: TrackId, added: Instant) {
  def timed(formatter: TimeFormatter) =
    TimedSentence(id, sentence, track, added, formatter.timing(added))
}

object SentenceRow {
  implicit val json = Json.format[SentenceRow]
}

case class TimedSentence(id: SentenceKey,
                         sentence: RawSentence,
                         track: TrackId,
                         added: Instant,
                         time: Timing)

object TimedSentence {
  implicit val json = Json.format[TimedSentence]
}

case class Sentences(sentences: Seq[RawSentence])

object Sentences {
  val Key = SentencesEvent.Key
  implicit val json = Json.format[Sentences]
}

case class TrackPointInput(lon: Longitude,
                           lat: Latitude,
                           coord: Coord,
                           boatSpeed: SpeedM,
                           waterTemp: Temperature,
                           depth: DistanceM,
                           depthOffset: DistanceM,
                           boatTime: java.sql.Timestamp,
                           track: TrackId,
                           trackIndex: Int,
                           diff: DistanceM)

object TrackPointInput {
  def forCoord(c: FullCoord, trackIndex: Int, diff: DistanceM): TrackPointInput =
    TrackPointInput(c.lng,
                    c.lat,
                    c.coord,
                    c.boatSpeed,
                    c.waterTemp,
                    c.depth,
                    c.depthOffset,
                    java.sql.Timestamp.from(c.boatTime),
                    c.from.track,
                    trackIndex,
                    diff)
}

case class CombinedCoord(id: TrackPointId,
                         lon: Longitude,
                         lat: Latitude,
                         coord: Coord,
                         boatSpeed: SpeedM,
                         waterTemp: Temperature,
                         depth: DistanceM,
                         depthOffset: DistanceM,
                         boatTime: java.sql.Timestamp,
                         date: LocalDate,
                         track: TrackId,
                         added: Instant) {

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
      boatTime.toInstant,
      date,
      track,
      added,
      sentences.map(_.timed(formatter)),
      formatter.timing(boatTime.toInstant)
    )

  def timed(formatter: TimeFormatter): TimedCoord = {
    val instant = boatTime.toInstant
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

case class CombinedFullCoord(id: TrackPointId,
                             lon: Longitude,
                             lat: Latitude,
                             coord: Coord,
                             boatSpeed: SpeedM,
                             waterTemp: Temperature,
                             depthMeters: DistanceM,
                             depthOffsetMeters: DistanceM,
                             boatTime: Instant,
                             date: LocalDate,
                             track: TrackId,
                             added: Instant,
                             sentences: Seq[TimedSentence],
                             time: Timing)

object CombinedFullCoord {
  val modern = Json.format[CombinedFullCoord]
  implicit val json = Format[CombinedFullCoord](
    modern,
    Writes(c => modern.writes(c) ++ Json.obj(
      "depth" -> c.depthMeters.toMillis.toLong,
      "depthOffset" -> c.depthOffsetMeters.toMillis.toLong))
  )
}

case class FullTrack(track: TrackRef, coords: Seq[CombinedFullCoord]) {
  def name = track.trackName
}

object FullTrack {
  implicit val json = Json.format[FullTrack]
}

case class TrackPointRow(id: TrackPointId,
                         lon: Longitude,
                         lat: Latitude,
                         coord: Coord,
                         boatSpeed: SpeedM,
                         waterTemp: Temperature,
                         depth: DistanceM,
                         depthOffset: DistanceM,
                         boatTime: java.sql.Timestamp,
                         track: TrackId,
                         trackIndex: Int,
                         diff: DistanceM,
                         added: Instant) {
  def dateTimeUtc = boatTime.toInstant.atOffset(ZoneOffset.UTC)
  def time = LocalTime.from(dateTimeUtc)
  def date = LocalDate.from(dateTimeUtc)
}

case class SentencePointLink(sentence: SentenceKey, point: TrackPointId)

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
