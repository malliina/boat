package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values._
import play.api.data.Mapping
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc.PathBindable

import scala.concurrent.duration.DurationLong

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

case class EasyDistance(track: TrackId, distance: Distance)

object EasyDistance {
  implicit val json = Json.format[EasyDistance]
}

case class JoinedTrack(track: TrackId, trackName: TrackName, trackAdded: Instant,
                       boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                       user: UserId, username: Username, email: Option[Email],
                       points: Int, start: Option[Instant], end: Option[Instant],
                       topSpeed: Option[Speed], avgSpeed: Option[Speed],
                       avgWaterTemp: Option[Temperature], distance: Distance) extends TrackLike {
  val startOrNow = start.getOrElse(Instant.now())
  val endOrNow = end.getOrElse(Instant.now())
  val duration = (endOrNow.toEpochMilli - startOrNow.toEpochMilli).millis

  def short = TrackMetaShort(track, trackName, boat, boatName, user, username)

  def strip = TrackRef(
    track, trackName, boat,
    boatName, user, username,
    points, Instants.format(startOrNow), startOrNow.toEpochMilli,
    Instants.format(endOrNow), endOrNow.toEpochMilli, Instants.formatRange(startOrNow, endOrNow),
    duration, distance, topSpeed, avgSpeed,
    avgWaterTemp
  )
}

object JoinedTrack {
  implicit val json = Json.format[JoinedTrack]
}

case class TrackNumbers(track: TrackId, start: Option[Instant], end: Option[Instant], topSpeed: Option[Speed])

case class TrackMeta(track: TrackId, trackName: TrackName, trackAdded: Instant,
                     boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                     user: UserId, username: Username, email: Option[Email]) {
  def short = TrackMetaShort(track, trackName, boat, boatName, user, username)
}

object TrackMeta {
  implicit val json = Json.format[TrackMeta]
}

case class BoatEvent(message: JsValue, from: TrackMeta)

object BoatEvent {
  implicit val json = Json.format[BoatEvent]
}

case class BoatJsonError(error: JsError, boat: BoatEvent)

case class SingleError(message: String)

object SingleError {
  implicit val json = Json.format[SingleError]
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
  implicit val html: Writeable[Errors] = Writeable.writeableOf_JsValue.map[Errors](e => Json.toJson(e))

  def apply(error: SingleError): Errors = Errors(Seq(error))
}

object BoatNames {
  val mapping: Mapping[BoatName] = play.api.data.Forms.nonEmptyText.transform(s => BoatName(s), b => b.name)

  def random() = BoatName(Utils.randomString(6))
}

object TrackNames {
  def random() = TrackName(Utils.randomString(6))
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

case class JoinedBoat(boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                      user: UserId, username: Username, email: Option[Email])

case class TrackInput(name: TrackName, boat: BoatId, avgSpeed: Option[Speed],
                      avgWaterTemp: Option[Temperature], points: Int, distance: Distance)

object TrackInput {
  def empty(name: TrackName, boat: BoatId): TrackInput = TrackInput(name, boat, None, None, 0, Distance.zero)
}

case class TrackRow(id: TrackId, name: TrackName, boat: BoatId,
                    avgSpeed: Option[Speed], avgWaterTemp: Option[Temperature],
                    points: Int, distance: Distance, added: Instant)

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

case class TrackPointId(id: Long) extends WrappedId

object TrackPointId extends IdCompanion[TrackPointId]

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
                           previous: Option[TrackPointId],
                           diff: Distance)

object TrackPointInput {
  def forCoord(c: FullCoord, trackIndex: Int, previous: Option[TrackPointId], diff: Distance): TrackPointInput =
    TrackPointInput(
      c.lng, c.lat, c.coord, c.boatSpeed,
      c.waterTemp, c.depth, c.depthOffset, c.boatTime,
      c.from.track, trackIndex, previous, diff)
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
                         added: Instant)

object CombinedCoord {
  implicit val json = Json.format[CombinedCoord]
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
                         previous: Option[TrackPointId],
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
