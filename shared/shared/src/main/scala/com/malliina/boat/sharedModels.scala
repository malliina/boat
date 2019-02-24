package com.malliina.boat

import com.malliina.boat.BoatJson.keyValued
import com.malliina.json.PrimitiveFormats
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values._
import play.api.libs.json._
import scalatags.generic.Bundle

import scala.concurrent.duration.Duration

case class Bearing(bearing: Int)

object Bearing {
  val north = apply(0)
  val east = apply(90)
  val south = apply(180)
  val west = apply(270)
}

case class FormattedTime(time: String) extends Wrapped(time)

object FormattedTime extends StringCompanion[FormattedTime]

case class FormattedDate(date: String) extends Wrapped(date)

object FormattedDate extends StringCompanion[FormattedDate]

case class FormattedDateTime(dateTime: String) extends Wrapped(dateTime)

object FormattedDateTime extends StringCompanion[FormattedDateTime]

case class Timing(date: FormattedDate,
                  time: FormattedTime,
                  dateTime: FormattedDateTime,
                  millis: Long)

object Timing {
  implicit val json = Json.format[Timing]
}

case class Times(start: Timing, end: Timing, range: String)

object Times {
  implicit val json = Json.format[Times]
}

case class Coord(lng: Double, lat: Double) {

  override def toString = s"($lng, $lat)"

  def toArray: Array[Double] = Array(lng, lat)

  def isValid = !lng.isNaN && !lat.isNaN

  def approx: String = {
    val lngStr = Coord.format(lng)
    val latStr = Coord.format(lat)
    s"$lngStr,$latStr"
  }
}

object Coord {
  def format(d: Double): String = {
    val trunc = (d * 100000).toInt.toDouble / 100000
    "%1.5f".format(trunc)
  }

  val Key = "coord"
  implicit val json: OFormat[Coord] = Json.format[Coord]
  // GeoJSON format
  val jsonArray = Format(
    Reads[Coord](json =>
      json.validate[List[Double]].flatMap {
        case lng :: lat :: _ => JsSuccess(Coord(lng, lat))
        case _ =>
          JsError(
            s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$json'.")
    }),
    Writes[Coord](c => Json.toJson(c.toArray))
  )
}

case class TimedCoord(id: TrackPointId,
                      coord: Coord,
                      boatTime: FormattedDateTime,
                      boatTimeMillis: Long,
                      boatTimeOnly: FormattedTime,
                      speed: Speed,
                      waterTemp: Temperature,
                      depth: Distance,
                      time: Timing) {
  def lng = coord.lng

  def lat = coord.lat
}

object TimedCoord {
  implicit val json = Json.format[TimedCoord]
}

case class AccessToken(token: String) extends Wrapped(token)

object AccessToken extends StringCompanion[AccessToken]

case class BoatName(name: String) extends Wrapped(name)

object BoatName extends StringCompanion[BoatName] {
  val Key = "boatName"
}

case class TrackName(name: String) extends Wrapped(name)

object TrackName extends StringCompanion[TrackName] {
  val Key = "track"
}

case class TrackTitle(title: String) extends Wrapped(title)

object TrackTitle extends StringCompanion[TrackTitle] {
  val Key = "title"
  val MaxLength = 191
}

case class TrackCanonical(name: String) extends Wrapped(name)

object TrackCanonical extends StringCompanion[TrackCanonical] {
  def apply(name: TrackName): TrackCanonical = TrackCanonical(name.name)
}

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
  val initialZda = RawSentence("$GPZDA,,00,00,0000,-03,00*66")
}

object Usernames {
  val anon = Username("anon")
}

case class Language(code: String) extends Wrapped(code)

object Language extends StringCompanion[Language] {
  val english = Language("en-US")
  val finnish = Language("fi-FI")
  val swedish = Language("sv-SE")
  val default = finnish
}

case class ChangeLanguage(language: Language)

object ChangeLanguage {
  implicit val json = Json.format[ChangeLanguage]
}

case class SimpleMessage(message: String)

object SimpleMessage {
  implicit val json = Json.format[SimpleMessage]
}

trait BoatTrackMeta extends BoatMeta {
  def track: TrackName
}

trait BoatMeta {
  def user: Username

  def boat: BoatName
}

case class BoatId(id: Long) extends WrappedId

object BoatId extends IdCompanion[BoatId]

case class TrackId(id: Long) extends WrappedId

object TrackId extends IdCompanion[TrackId]

case class PushId(id: Long) extends WrappedId

object PushId extends IdCompanion[PushId]

case class PushToken(token: String) extends Wrapped(token)

object PushToken extends StringCompanion[PushToken]

sealed abstract class MobileDevice(val name: String) {
  override def toString: String = name
}

object MobileDevice extends ValidatingCompanion[String, MobileDevice] {
  val Key = "device"
  val all: Seq[MobileDevice] = Seq(IOS, Android)

  def apply(s: String): MobileDevice = build(s).getOrElse(Unknown(s))

  override def build(input: String): Either[ErrorMessage, MobileDevice] =
    all
      .find(_.name.toLowerCase == input.toLowerCase)
      .toRight(ErrorMessage(s"Unknown device type: '$input'."))

  override def write(t: MobileDevice): String = t.name

  case object IOS extends MobileDevice("ios")

  case object Android extends MobileDevice("android")

  case class Unknown(s: String) extends MobileDevice(s)

}

case class Boat(id: BoatId, name: BoatName, token: BoatToken, addedMillis: Long)

object Boat {
  implicit val json = Json.format[Boat]
}

trait MinimalUserInfo {
  def username: Username

  def language: Language
}

object MinimalUserInfo {
  def anon: MinimalUserInfo = SimpleUserInfo(Usernames.anon, Language.default)
}

case class SimpleUserInfo(username: Username, language: Language) extends MinimalUserInfo

case class UserInfo(id: UserId,
                    username: Username,
                    email: Option[Email],
                    language: Language,
                    boats: Seq[Boat],
                    enabled: Boolean,
                    addedMillis: Long) extends MinimalUserInfo

object UserInfo {
  implicit val json = Json.format[UserInfo]
}

case class UserContainer(user: UserInfo)

object UserContainer {
  implicit val json = Json.format[UserContainer]
}

trait TrackMetaLike {
  def boatName: BoatName

  def trackName: TrackName

  def username: Username
}

trait TrackLike extends TrackMetaLike {
  def duration: Duration
}

case class TrackMetaShort(track: TrackId,
                          trackName: TrackName,
                          boat: BoatId,
                          boatName: BoatName,
                          username: Username)
    extends TrackMetaLike

object TrackMetaShort {
  implicit val json = Json.format[TrackMetaShort]
}

case class TrackRef(track: TrackId,
                    trackName: TrackName,
                    trackTitle: Option[TrackTitle],
                    canonical: TrackCanonical,
                    boat: BoatId,
                    boatName: BoatName,
                    username: Username,
                    points: Int,
                    start: FormattedDateTime,
                    startMillis: Long,
                    end: FormattedDateTime,
                    endMillis: Long,
                    startEndRange: String,
                    duration: Duration,
                    distance: Distance,
                    topSpeed: Option[Speed],
                    avgSpeed: Option[Speed],
                    avgWaterTemp: Option[Temperature],
                    topPoint: TimedCoord,
                    times: Times)
    extends TrackLike {
  def describe = trackTitle.map(_.title).getOrElse(trackName.name)
}

object TrackRef {
  implicit val durationFormat = PrimitiveFormats.durationFormat
  implicit val json = Json.format[TrackRef]
}

case class TrackResponse(track: TrackRef)

object TrackResponse {
  implicit val json = Json.format[TrackResponse]
}

case class InsertedTrackPoint(point: TrackPointId, track: TrackRef)

//object InsertedPoint {
//  implicit val json = Json.format[InsertedPoint]
//}

case class TrackPointId(id: Long) extends WrappedId

object TrackPointId extends IdCompanion[TrackPointId]

case class BoatUser(track: TrackName, boat: BoatName, user: Username) extends BoatTrackMeta

case class BoatInfo(boatId: BoatId,
                    boat: BoatName,
                    user: Username,
                    language: Language,
                    tracks: Seq[TrackRef])

object BoatInfo {
  implicit val json = Json.format[BoatInfo]
}

case class UserBoats(user: Username, language: Language, boats: Seq[BoatInfo])

object UserBoats {
  val anon = UserBoats(Usernames.anon, Language.default, Nil)
}

case class TrackBrief(trackName: TrackName, added: String, addedMillis: Long)

object TrackBrief {
  implicit val json = Json.format[TrackBrief]
}

case class TrackSummary(track: TrackRef)

object TrackSummary {
  implicit val json = Json.format[TrackSummary]
}

case class TrackSummaries(tracks: Seq[TrackSummary])

object TrackSummaries {
  implicit val json = Json.format[TrackSummaries]
}

case class Tracks(tracks: Seq[TrackRef])

object Tracks {
  implicit val json = Json.format[Tracks]
}

case class CoordsEvent(coords: Seq[TimedCoord], from: TrackRef) extends BoatFrontEvent {
  def isEmpty = coords.isEmpty

  def sample(every: Int): CoordsEvent =
    if (every <= 1) this
    else copy(coords = coords.grouped(every).flatMap(_.headOption).toList, from)

  def addCoords(newCoords: Seq[TimedCoord]) = copy(coords = coords ++ newCoords)

  def toMap: Map[String, JsValue] = coords.map(tc => tc.id.toString -> Json.toJson(tc)).toMap
}

object CoordsEvent {
  val Key = "coords"
  implicit val coordJson = Coord.json
  implicit val json: OFormat[CoordsEvent] = keyValued(Key, Json.format[CoordsEvent])
}

case class CoordsBatch(events: Seq[CoordsEvent]) extends FrontEvent {
  override def isIntendedFor(user: Username): Boolean =
    events.forall(_.isIntendedFor(user))
}

object CoordsBatch {
  implicit val json = Json.format[CoordsBatch]
}

case class SentencesEvent(sentences: Seq[RawSentence], from: TrackMetaShort) extends BoatFrontEvent

object SentencesEvent {
  val Key = "sentences"
  implicit val json = keyValued(Key, Json.format[SentencesEvent])
}

case class PingEvent(sent: Long) extends FrontEvent {
  override def isIntendedFor(user: Username) = true
}

object PingEvent {
  val Key = "ping"
  implicit val json = keyValued(Key, Json.format[PingEvent])
}

case class VesselMessages(vessels: Seq[VesselInfo]) extends FrontEvent {
  override def isIntendedFor(user: Username): Boolean = true
}

object VesselMessages {
  val Key = "vessels"
  implicit val json = keyValued(Key, Json.format[VesselMessages])
  val empty = VesselMessages(Nil)
}

sealed trait FrontEvent {
  def isIntendedFor(user: Username): Boolean
}

sealed trait BoatFrontEvent extends FrontEvent {
  def from: TrackMetaLike

  // Anonymous users receive all live boat updates by design
  override def isIntendedFor(user: Username): Boolean =
    from.username == user || user == Usernames.anon
}

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    VesselMessages.json
      .reads(json)
      .orElse(CoordsEvent.json.reads(json))
      .orElse(CoordsBatch.json.reads(json))
      .orElse(SentencesEvent.json.reads(json))
      .orElse(PingEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case se @ SentencesEvent(_, _) => SentencesEvent.json.writes(se)
    case ce @ CoordsEvent(_, _)    => CoordsEvent.json.writes(ce)
    case cb @ CoordsBatch(_)       => CoordsBatch.json.writes(cb)
    case pe @ PingEvent(_)         => PingEvent.json.writes(pe)
    case vs @ VesselMessages(_)    => VesselMessages.json.writes(vs)
  }
}

case class SentencesMessage(sentences: Seq[RawSentence]) {
  def toEvent(from: TrackMetaShort) = SentencesEvent(sentences, from)
}

object SentencesMessage {
  val Key = "sentences"
  implicit val json = keyValued(Key, Json.format[SentencesMessage])
}

object BoatJson {
  val EventKey = "event"
  val BodyKey = "body"

  def empty[T](build: => T): OFormat[T] =
    OFormat[T](Reads(_ => JsSuccess(build)), OWrites[T](_ => Json.obj()))

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

abstract class Companion[Raw, T](implicit jsonFormat: Format[Raw], o: Ordering[Raw]) {
  def apply(raw: Raw): T

  def raw(t: T): Raw

  implicit val format: Format[T] = Format(
    Reads[T](in => in.validate[Raw].map(apply)),
    Writes[T](t => Json.toJson(raw(t)))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
}

class ModelHtml[Builder, Output <: FragT, FragT](val bundle: Bundle[Builder, Output, FragT]) {

  import bundle.all._

  implicit def wrappedFrag[T <: Wrapped](t: T): Frag = stringFrag(t.value)
}
