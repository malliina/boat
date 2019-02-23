package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{Distance, Speed, Temperature}
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

case class Layers(marks: Seq[String], ais: AisConf)

object Layers {
  implicit val json = Json.format[Layers]
  import MapboxStyles._

  val default = Layers(marksLayers, AisConf(AisVesselLayer, AisTrailLayer, AisVesselIcon))
}

case class ClientConf(languages: Languages, layers: Layers)

object ClientConf {
  implicit val json = Json.format[ClientConf]
  val default = ClientConf(Languages(Lang.fi, Lang.se, Lang.en), Layers.default)
}

case class UserToken(token: String) extends Wrapped(token)

object UserToken extends StringCompanion[UserToken] {
  val minLength = 3

  override def build(in: String): Either[ErrorMessage, UserToken] =
    if (in.length >= minLength) Right(UserToken(in))
    else Left(ErrorMessage(s"Too short token. Minimum length: $minLength, was: ${in.length}."))

  def random(): UserToken = UserToken(Utils.randomString(length = 8))
}

case class AppMeta(name: String, version: String, gitHash: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]
  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)
}

case class JoinedTrack(track: TrackId,
                       trackName: TrackName,
                       trackTitle: Option[TrackTitle],
                       canonical: TrackCanonical,
                       trackAdded: Instant,
                       boat: BoatId,
                       boatName: BoatName,
                       boatToken: BoatToken,
                       user: UserId,
                       username: Username,
                       email: Option[Email],
                       language: Language,
                       points: Int,
                       start: Option[Instant],
                       end: Option[Instant],
                       topSpeed: Option[Speed],
                       avgSpeed: Option[Speed],
                       avgWaterTemp: Option[Temperature],
                       distance: Distance,
                       topPoint: CombinedCoord)
    extends TrackLike {
  val startOrNow = start.getOrElse(Instant.now())
  val endOrNow = end.getOrElse(Instant.now())
  val duration = (endOrNow.toEpochMilli - startOrNow.toEpochMilli).millis

  /**
    * @return a Scala.js -compatible representation of this track
    */
  def strip = TrackRef(
    track,
    trackName,
    trackTitle,
    canonical,
    boat,
    boatName,
    username,
    points,
    Instants.formatDateTime(startOrNow),
    startOrNow.toEpochMilli,
    Instants.formatDateTime(endOrNow),
    endOrNow.toEpochMilli,
    Instants.formatRange(startOrNow, endOrNow),
    duration,
    distance,
    topSpeed,
    avgSpeed,
    avgWaterTemp,
    topPoint.timed,
    Instants.times(startOrNow, endOrNow)
  )
}

case class TrackNumbers(track: TrackId,
                        start: Option[Instant],
                        end: Option[Instant],
                        topSpeed: Option[Speed])

case class TrackMeta(track: TrackId,
                     trackName: TrackName,
                     trackAdded: Instant,
                     boat: BoatId,
                     boatName: BoatName,
                     boatToken: BoatToken,
                     user: UserId,
                     username: Username,
                     email: Option[Email]) {
  def short = TrackMetaShort(track, trackName, boat, boatName, username)
}

object TrackMeta {
  implicit val json = Json.format[TrackMeta]
}

case class BoatEvent(message: JsValue, from: TrackMeta)

object BoatEvent {
  implicit val json = Json.format[BoatEvent]
}

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

object Bindables {
  implicit val trackName: PathBindable[TrackName] =
    PathBindable.bindableString.transform[TrackName](s => TrackName(s), t => t.name)

  implicit val boatName: PathBindable[BoatName] =
    PathBindable.bindableString.transform[BoatName](s => BoatName(s), t => t.name)

  implicit val boatId: PathBindable[BoatId] =
    PathBindable.bindableLong.transform[BoatId](s => BoatId(s), id => id.id)
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
                      avgSpeed: Option[Speed],
                      avgWaterTemp: Option[Temperature],
                      points: Int,
                      distance: Distance,
                      canonical: TrackCanonical)

object TrackInput {
  def empty(name: TrackName, boat: BoatId): TrackInput =
    TrackInput(name, boat, None, None, 0, Distance.zero, TrackCanonical(name))
}

case class TrackRow(id: TrackId,
                    name: TrackName,
                    boat: BoatId,
                    avgSpeed: Option[Speed],
                    avgWaterTemp: Option[Temperature],
                    points: Int,
                    distance: Distance,
                    title: Option[TrackTitle],
                    canonical: TrackCanonical,
                    added: Instant)

case class SentenceKey(id: Long) extends WrappedId

object SentenceKey extends IdCompanion[SentenceKey]

case class SentenceInput(sentence: RawSentence, track: TrackId)

case class KeyedSentence(key: SentenceKey, sentence: RawSentence, from: TrackMetaShort)

case class SentenceRow(id: SentenceKey, sentence: RawSentence, track: TrackId, added: Instant)

object SentenceRow {
  implicit val json = Json.format[SentenceRow]
}

case class Sentences(sentences: Seq[RawSentence])

object Sentences {
  val Key = SentencesEvent.Key
  implicit val json = Json.format[Sentences]
}

case class TrackPointInput(lon: Double,
                           lat: Double,
                           coord: Coord,
                           boatSpeed: Speed,
                           waterTemp: Temperature,
                           depth: Distance,
                           depthOffset: Distance,
                           boatTime: Instant,
                           track: TrackId,
                           trackIndex: Int,
                           diff: Distance)

object TrackPointInput {
  def forCoord(c: FullCoord, trackIndex: Int, diff: Distance): TrackPointInput =
    TrackPointInput(c.lng,
                    c.lat,
                    c.coord,
                    c.boatSpeed,
                    c.waterTemp,
                    c.depth,
                    c.depthOffset,
                    c.boatTime,
                    c.from.track,
                    trackIndex,
                    diff)
}

case class CombinedCoord(id: TrackPointId,
                         lon: Double,
                         lat: Double,
                         coord: Coord,
                         boatSpeed: Speed,
                         waterTemp: Temperature,
                         depth: Distance,
                         depthOffset: Distance,
                         boatTime: Instant,
                         date: LocalDate,
                         track: TrackId,
                         added: Instant) {
  def toFull(sentences: Seq[SentenceRow]): CombinedFullCoord = CombinedFullCoord(
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
    sentences
  )

  def timed = TimedCoord(
    id,
    coord,
    Instants.formatDateTime(boatTime),
    boatTime.toEpochMilli,
    Instants.formatTime(boatTime),
    boatSpeed,
    waterTemp,
    depth,
    Instants.timing(boatTime)
  )
}

object CombinedCoord {
  implicit val json = Json.format[CombinedCoord]
}

case class TrackInfo(coords: Seq[CombinedCoord], topPoint: Option[CombinedCoord])

object TrackInfo {
  implicit val json = Json.format[TrackInfo]
}

case class CombinedFullCoord(id: TrackPointId,
                             lon: Double,
                             lat: Double,
                             coord: Coord,
                             boatSpeed: Speed,
                             waterTemp: Temperature,
                             depth: Distance,
                             depthOffset: Distance,
                             boatTime: Instant,
                             date: LocalDate,
                             track: TrackId,
                             added: Instant,
                             sentences: Seq[SentenceRow])

object CombinedFullCoord {
  implicit val json = Json.format[CombinedFullCoord]
}

case class FullTrack(track: TrackRef, coords: Seq[CombinedFullCoord]) {
  def name = track.trackName
}

object FullTrack {
  implicit val json = Json.format[FullTrack]
}

case class TrackPointRow(id: TrackPointId,
                         lon: Double,
                         lat: Double,
                         coord: Coord,
                         boatSpeed: Speed,
                         waterTemp: Temperature,
                         depth: Distance,
                         depthOffset: Distance,
                         boatTime: Instant,
                         track: TrackId,
                         trackIndex: Int,
                         diff: Distance,
                         added: Instant) {
  def dateTimeUtc = boatTime.atOffset(ZoneOffset.UTC)

  def time = LocalTime.from(dateTimeUtc)

  def date = LocalDate.from(dateTimeUtc)
}

case class SentencePointLink(sentence: SentenceKey, point: TrackPointId)

case class TrackPoint(coord: Coord, time: Instant, waterTemp: Double, wind: Double)

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
