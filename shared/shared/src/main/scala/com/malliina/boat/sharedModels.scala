package com.malliina.boat

import com.malliina.boat.BoatJson.keyValued
import com.malliina.values.{StringCompanion, Wrapped}
import play.api.libs.json._

case class Coord(lng: Double, lat: Double)

object Coord {
  val Key = "coord"
  implicit val json = Json.format[Coord]
  // GeoJSON format
  val jsonArray = Format(
    Reads[Coord](json => json.validate[List[Double]].flatMap {
      case lng :: lat :: _ => JsSuccess(Coord(lng, lat))
      case _ => JsError(s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$json'.")
    }),
    Writes[Coord](c => Json.toJson(Seq(c.lng, c.lat)))
  )
}

case class Geometry(`type`: String, coordinates: Seq[Coord]) {
  def addCoords(coords: Seq[Coord]): Geometry = copy(coordinates = coordinates ++ coords)
}

object Geometry {
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[Geometry]
}

case class Feature(`type`: String, geometry: Geometry) {
  def addCoords(coords: Seq[Coord]) = copy(geometry = geometry.addCoords(coords))
}

object Feature {
  implicit val json = Json.format[Feature]
}

case class FeatureCollection(`type`: String, features: Seq[Feature]) {
  def addCoords(coords: Seq[Coord]) = copy(features = features.map(_.addCoords(coords)))
}

object FeatureCollection {
  implicit val json = Json.format[FeatureCollection]
}

case class AnimationSource(`type`: String, data: FeatureCollection)

object AnimationSource {
  implicit val json = Json.format[AnimationSource]
}

case class Layout(`line-join`: String, `line-cap`: String)

object Layout {
  implicit val json = Json.format[Layout]
}

case class Paint(`line-color`: String, `line-width`: Int)

object Paint {
  implicit val json = Json.format[Paint]
}

case class Animation(id: String,
                     `type`: String,
                     source: AnimationSource,
                     layout: Layout,
                     paint: Paint)

object Animation {
  implicit val json = Json.format[Animation]
}

case class AccessToken(token: String) extends Wrapped(token)

object AccessToken extends StringCompanion[AccessToken]

case class BoatName(name: String) extends Wrapped(name)

object BoatName extends StringCompanion[BoatName]

case class TrackName(name: String) extends Wrapped(name)

object TrackName extends StringCompanion[TrackName]

case class BoatToken(token: String) extends Wrapped(token)

object BoatToken extends StringCompanion[BoatToken]

/** An NMEA Sentence.
  *
  * "An NMEA sentence consists of a start delimiter, followed by a comma-separated sequence of fields,
  * followed by the character * (ASCII 42), the checksum and an end-of-line marker."
  *
  * "The first field of a sentence is called the "tag" and normally consists of a two-letter talker ID followed by a three-letter type code."
  *
  * "Sentences are terminated by a CRLF sequence."
  *
  * "Maximum sentence length, including the $ and CRLF is 82 bytes."
  *
  * @param sentence sentence as a String
  * @see http://www.catb.org/gpsd/NMEA.html
  */
case class RawSentence(sentence: String) extends Wrapped(sentence)

object RawSentence extends StringCompanion[RawSentence] {
  val MaxLength = 82
}

case class UserId(id: Long) extends WrappedId

object UserId extends IdCompanion[UserId]

case class User(name: String) extends Wrapped(name)

object User extends StringCompanion[User]

trait BoatMeta {
  def user: User

  def boat: BoatName

  def track: TrackName
}

case class BoatId(id: Long) extends WrappedId

object BoatId extends IdCompanion[BoatId]

case class TrackId(id: Long) extends WrappedId

object TrackId extends IdCompanion[TrackId]

case class BoatUser(track: TrackName, boat: BoatName, user: User) extends BoatMeta {
  def toBoat(boatId: BoatId) = BoatInfo(boatId, boat, user)
}

case class BoatInfo(boatId: BoatId, boat: BoatName, user: User)

object BoatInfo {
  implicit val json = Json.format[BoatInfo]
}

case class JoinedTrack(track: TrackId, trackName: TrackName, boat: BoatId, boatName: BoatName, user: UserId, username: User)

object JoinedTrack {
  implicit val json = Json.format[JoinedTrack]
}

case class CoordsEvent(coords: Seq[Coord], from: JoinedTrack) extends BoatFrontEvent {
  def isEmpty = coords.isEmpty
}

object CoordsEvent {
  val Key = "coords"
  implicit val coordJson = Coord.json
  implicit val json = keyValued(Key, Json.format[CoordsEvent])
}

case class SentencesEvent(sentences: Seq[RawSentence], from: JoinedTrack) extends BoatFrontEvent

object SentencesEvent {
  val Key = "sentences"
  implicit val json = keyValued(Key, Json.format[SentencesEvent])
}

case class PingEvent(sent: Long) extends FrontEvent {
  override def isIntendedFor(user: User) = true
}

object PingEvent {
  val Key = "ping"
  implicit val json = keyValued(Key, Json.format[PingEvent])
}

sealed trait FrontEvent {
  def isIntendedFor(user: User): Boolean
}

sealed trait BoatFrontEvent extends FrontEvent {
  def from: JoinedTrack

  override def isIntendedFor(user: User): Boolean = from.username == user
}

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    CoordsEvent.json.reads(json)
      .orElse(SentencesEvent.json.reads(json))
      .orElse(PingEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case se@SentencesEvent(_, _) => SentencesEvent.json.writes(se)
    case ce@CoordsEvent(_, _) => CoordsEvent.json.writes(ce)
    case pe@PingEvent(_) => PingEvent.json.writes(pe)
  }
}

case class SentencesMessage(sentences: Seq[RawSentence]) {
  def toEvent(from: JoinedTrack) = SentencesEvent(sentences, from)
}

object SentencesMessage {
  val Key = "sentences"
  implicit val json = keyValued(Key, Json.format[SentencesMessage])
}

object BoatJson {
  val EventKey = "event"
  val BodyKey = "body"

  def empty[T](build: => T): OFormat[T] = OFormat[T](Reads(_ => JsSuccess(build)), OWrites[T](_ => Json.obj()))

  /** A JSON format for objects of type T that contains a top-level "event" key and further data in "body".
    */
  def keyValued[T](value: String, payload: OFormat[T]): OFormat[T] = {
    val reader: Reads[T] = Reads { json =>
      for {
        event <- (json \ EventKey).validate[String]
        if event == value
        body <- (json \ BodyKey).validate[T](payload)
      } yield body
    }
    val writer = OWrites[T] { t =>
      Json.obj(EventKey -> value, BodyKey -> payload.writes(t))
    }
    OFormat(reader, writer)
  }
}

trait WrappedId {
  def id: Long

  override def toString = s"$id"
}

abstract class IdCompanion[T <: WrappedId] extends Companion[Long, T] {
  override def raw(t: T) = t.id
}

abstract class Companion[Raw, T](implicit jsonFormat: Format[Raw], o: Ordering[Raw]) {
  def apply(raw: Raw): T

  def raw(t: T): Raw

  implicit val format: Format[T] = Format(
    Reads[T](in => in.validate[Raw].map(apply)),
    Writes[T](t => Json.toJson(raw(t)))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
}
