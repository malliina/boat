package com.malliina.boat

import com.malliina.boat.BoatJson.keyValued
import com.malliina.json.PrimitiveFormats
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values._
import play.api.libs.json._

import scala.concurrent.duration.Duration

case class Coord(lng: Double, lat: Double) {
  def toArray: Array[Double] = Array(lng, lat)
}

object Coord {
  val Key = "coord"
  implicit val json = Json.format[Coord]
  // GeoJSON format
  val jsonArray = Format(
    Reads[Coord](json => json.validate[List[Double]].flatMap {
      case lng :: lat :: _ => JsSuccess(Coord(lng, lat))
      case _ => JsError(s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$json'.")
    }),
    Writes[Coord](c => Json.toJson(c.toArray))
  )
}

case class TimedCoord(coord: Coord,
                      boatTime: String,
                      boatTimeMillis: Long,
                      speed: Speed,
                      waterTemp: Temperature,
                      depth: Distance) {
  def lng = coord.lng

  def lat = coord.lat
}

object TimedCoord {
  implicit val json = Json.format[TimedCoord]
}

case class LineGeometry(`type`: String, coordinates: Seq[Coord]) extends Geometry(LineGeometry.LineString) {
  override def updateCoords(coords: Seq[Coord]): LineGeometry =
    copy(coordinates = coordinates ++ coords)

  override def coords: Seq[Coord] = coordinates
}

object LineGeometry {
  val LineString = "LineString"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[LineGeometry]
}

case class PointGeometry(`type`: String, coordinates: Coord) extends Geometry(PointGeometry.Point) {
  override def updateCoords(coords: Seq[Coord]): PointGeometry =
    PointGeometry(`type`, coords.headOption.getOrElse(coordinates))

  override def coords: Seq[Coord] = Seq(coordinates)
}

object PointGeometry {
  val Point = "Point"
  implicit val coord = Coord.jsonArray
  implicit val json = Json.format[PointGeometry]
}

sealed abstract class Geometry(typeName: String) {
  def updateCoords(coords: Seq[Coord]): Geometry

  def coords: Seq[Coord]
}

object Geometry {
  val Type = "type"
  val all = Seq(LineGeometry, PointGeometry)
  implicit val writer = Writes[Geometry] {
    case lg@LineGeometry(_, _) => Json.toJson(lg)
    case pg@PointGeometry(_, _) => Json.toJson(pg)
  }
  implicit val reader = Reads[Geometry] { json =>
    (json \ Geometry.Type).validate[String].flatMap {
      case LineGeometry.LineString => LineGeometry.json.reads(json)
      case PointGeometry.Point => PointGeometry.json.reads(json)
      case other => JsError(s"Unsupported geometry type: '$other'.")
    }
  }
}

case class Feature(`type`: String, geometry: Geometry) {
  def addCoords(coords: Seq[Coord]) = copy(geometry = geometry.updateCoords(coords))
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

case class LineLayout(`line-join`: String, `line-cap`: String) extends Layout

object LineLayout {
  implicit val json = Json.format[LineLayout]
}

case class ImageLayout(`icon-image`: String, `icon-size`: Int) extends Layout

object ImageLayout {
  implicit val json = Json.format[ImageLayout]
}

sealed trait Layout

object Layout {
  implicit val writer = Writes[Layout] {
    case ll@LineLayout(_, _) => Json.toJson(ll)
    case il@ImageLayout(_, _) => Json.toJson(il)
  }
}

case class Paint(`line-color`: String, `line-width`: Int)

object Paint {
  implicit val json = Json.format[Paint]
}

sealed abstract class LayerType(val name: String)

object LayerType {
  implicit val writer = Writes[LayerType] { l => Json.toJson(l.name) }
}

case object LineLayer extends LayerType("line")

case object SymbolLayer extends LayerType("symbol")

case class Animation(id: String,
                     `type`: LayerType,
                     source: AnimationSource,
                     layout: Layout,
                     paint: Option[Paint])

object Animation {
  implicit val json = Json.writes[Animation]
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
  val initialZda = RawSentence("$GPZDA,,00,00,0000,-03,00*66")
}

object Usernames {
  val anon = Username("anon")
}

trait BoatMeta {
  def user: Username

  def boat: BoatName

  def track: TrackName
}

case class BoatId(id: Long) extends WrappedId

object BoatId extends IdCompanion[BoatId]

case class TrackId(id: Long) extends WrappedId

object TrackId extends IdCompanion[TrackId]

trait TrackMetaLike {
  def boatName: BoatName

  def trackName: TrackName

  def username: Username
}

case class TrackMetaShort(track: TrackId, trackName: TrackName,
                          boat: BoatId, boatName: BoatName,
                          user: UserId, username: Username) extends TrackMetaLike

object TrackMetaShort {
  implicit val json = Json.format[TrackMetaShort]
}

case class TrackRef(track: TrackId, trackName: TrackName, boat: BoatId,
                    boatName: BoatName, user: UserId, username: Username,
                    points: Int, start: String, startMillis: Long,
                    end: String, endMillis: Long, startEndRange: String,
                    duration: Duration, distance: Distance,
                    topSpeed: Option[Speed], avgSpeed: Option[Speed],
                    avgWaterTemp: Option[Temperature]) extends TrackLike

object TrackRef {
  implicit val durationFormat = PrimitiveFormats.durationFormat
  implicit val json = Json.format[TrackRef]
}

case class BoatUser(track: TrackName, boat: BoatName, user: Username) extends BoatMeta

case class BoatInfo(boatId: BoatId, boat: BoatName, user: Username, tracks: Seq[TrackRef])

object BoatInfo {
  implicit val json = Json.format[BoatInfo]
}

trait TrackLike {
  def username: Username

  def boatName: BoatName

  def trackName: TrackName

  def duration: Duration
}

case class TrackBrief(trackName: TrackName, added: String, addedMillis: Long)

object TrackBrief {
  implicit val json = Json.format[TrackBrief]
}

case class TrackStats(points: Int, first: String, firstMillis: Long, last: String, lastMillis: Long, duration: Duration)

object TrackStats {
  implicit val durationFormat = PrimitiveFormats.durationFormat
  implicit val json = Json.format[TrackStats]
}

case class TrackSummary(track: TrackRef, stats: TrackStats)

object TrackSummary {
  implicit val json = Json.format[TrackSummary]
}

case class TrackSummaries(tracks: Seq[TrackSummary])

object TrackSummaries {
  implicit val json = Json.format[TrackSummaries]
}

case class CoordsEvent(coords: Seq[TimedCoord], from: TrackMetaShort) extends BoatFrontEvent {
  def isEmpty = coords.isEmpty

  def sample(every: Int): CoordsEvent =
    copy(coords = coords.grouped(every).flatMap(_.headOption).toList, from)
}

object CoordsEvent {
  val Key = "coords"
  implicit val coordJson = Coord.json
  implicit val json = keyValued(Key, Json.format[CoordsEvent])
}

case class CoordsBatch(events: Seq[CoordsEvent]) extends FrontEvent {
  override def isIntendedFor(user: Username): Boolean = events.forall(_.isIntendedFor(user))
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

sealed trait FrontEvent {
  def isIntendedFor(user: Username): Boolean
}

sealed trait BoatFrontEvent extends FrontEvent {
  def from: TrackMetaShort

  // Anonymous users receive all live boat updates by design
  override def isIntendedFor(user: Username): Boolean = from.username == user || user == Usernames.anon
}

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    CoordsEvent.json.reads(json)
      .orElse(CoordsBatch.json.reads(json))
      .orElse(SentencesEvent.json.reads(json))
      .orElse(PingEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case se@SentencesEvent(_, _) => SentencesEvent.json.writes(se)
    case ce@CoordsEvent(_, _) => CoordsEvent.json.writes(ce)
    case cb@CoordsBatch(_) => CoordsBatch.json.writes(cb)
    case pe@PingEvent(_) => PingEvent.json.writes(pe)
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

abstract class Companion[Raw, T](implicit jsonFormat: Format[Raw], o: Ordering[Raw]) {
  def apply(raw: Raw): T

  def raw(t: T): Raw

  implicit val format: Format[T] = Format(
    Reads[T](in => in.validate[Raw].map(apply)),
    Writes[T](t => Json.toJson(raw(t)))
  )

  implicit val ordering: Ordering[T] = o.on(raw)
}
