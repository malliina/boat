package com.malliina.boat

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}

import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.Distance
import play.api.http.Writeable
import play.api.libs.json._

case class AppMeta(name: String, version: String, gitHash: String)

object AppMeta {
  implicit val json = Json.format[AppMeta]
  val default = AppMeta(BuildInfo.name, BuildInfo.version, BuildInfo.gitHash)
}

case class JoinedTrack(track: TrackId, trackName: TrackName, trackAdded: Instant,
                       boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                       user: UserId, username: User, email: Option[UserEmail],
                       points: Int, start: Option[Instant], end: Option[Instant]) extends TrackLike {
  val startOrNow = start.getOrElse(Instant.now())
  val endOrNow = end.getOrElse(Instant.now())

  def strip(distance: Distance) = TrackRef(
    track, trackName, boat,
    boatName, user, username,
    points, Instants.format(startOrNow), startOrNow.toEpochMilli,
    Instants.format(endOrNow), endOrNow.toEpochMilli, Instants.formatRange(startOrNow, endOrNow),
    distance
  )
}

object JoinedTrack {
  implicit val json = Json.format[JoinedTrack]
}

case class BoatEvent(message: JsValue, from: JoinedTrack)

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
  def random() = BoatName(Utils.randomString(6))
}

object TrackNames {
  def random() = TrackName(Utils.randomString(6))
}

object BoatTokens {
  def random() = BoatToken(Utils.randomString(8))
}

case class BoatInput(name: BoatName, token: BoatToken, owner: UserId)

case class BoatRow(id: BoatId, name: BoatName, token: BoatToken, owner: UserId, added: Instant)

case class TrackInput(name: TrackName, boat: BoatId)

case class TrackRow(id: TrackId, name: TrackName, boat: BoatId, added: Instant)

case class SentenceKey(id: Long) extends WrappedId

object SentenceKey extends IdCompanion[SentenceKey]

case class SentenceInput(sentence: RawSentence, track: TrackId)

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

case class TrackPointInput(lon: Double, lat: Double, boatTime: Instant, track: TrackId)

object TrackPointInput {
  def forCoord(coord: FullCoord): TrackPointInput =
    TrackPointInput(coord.lng, coord.lat, coord.boatTime, coord.from.track)
}

case class CombinedCoord(id: TrackPointId, lon: Double, lat: Double, boatTime: Instant, date: LocalDate, track: TrackId, added: Instant)

case class TrackPointRow(id: TrackPointId, lon: Double, lat: Double, boatTime: Instant, track: TrackId, added: Instant) {
  def toCoord = Coord(lon, lat)

  def dateTimeUtc = boatTime.atOffset(ZoneOffset.UTC)

  def time = LocalTime.from(dateTimeUtc)

  def date = LocalDate.from(dateTimeUtc)
}

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
