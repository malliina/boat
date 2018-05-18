package com.malliina.boat

import java.time.Instant

import play.api.http.Writeable
import play.api.libs.json._

case class BoatEvent(message: JsValue, from: BoatInfo)

object BoatEvent {
  implicit val json = Json.format[BoatEvent]
}

case class SingleError(message: String)

object SingleError {
  implicit val json = Json.format[SingleError]
}

case class Errors(errors: Seq[SingleError])

object Errors {
  implicit val json = Json.format[Errors]
  implicit val html: Writeable[Errors] = Writeable.writeableOf_JsValue.map[Errors](e => Json.toJson(e))

  def apply(message: String): Errors = Errors(Seq(SingleError(message)))
}

case class BoatId(id: Long) extends WrappedId

object BoatId extends IdCompanion[BoatId]

object BoatNames {
  def random() = BoatName(Utils.randomString(6))
}

object TrackNames {
  def random() = TrackName(Utils.randomString(6))
}

case class BoatRow(id: BoatId, name: BoatName, owner: UserId, added: Instant)

case class TrackRow(id: TrackId, name: TrackName, boat: BoatId, added: Instant)

case class UserId(id: Long) extends WrappedId

object UserId extends IdCompanion[UserId]

case class SentenceKey(id: Long) extends WrappedId

object SentenceKey extends IdCompanion[SentenceKey]

case class SentenceRow(id: SentenceKey, sentence: RawSentence, boat: BoatId, added: Instant)

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

case class TrackPointRow(id: TrackPointId, lon: Double, lat: Double, track: TrackId, added: Instant)

case class TrackPoint(coord: Coord, time: Instant, waterTemp: Double, wind: Double)

object TrackPoint {
  implicit val json = Json.format[TrackPoint]
}

case class TrackId(id: Long) extends WrappedId

object TrackId extends IdCompanion[TrackId]

case class Track(id: TrackId, name: TrackName, points: Seq[TrackPoint])

object Track {
  implicit val json = Json.format[Track]

  def randomName() = Utils.randomString(6)
}

case class RouteId(id: Long) extends WrappedId

object RouteId extends IdCompanion[RouteId]

case class Route(id: RouteId, name: String, points: Seq[Coord])

object Route {
  implicit val json = Json.format[Route]
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
