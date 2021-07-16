package com.malliina.boat

import com.malliina.boat.BoatJson.keyValued
import com.malliina.json.PrimitiveFormats
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values._
import play.api.libs.json._
import scalatags.generic.Bundle

import scala.concurrent.duration.Duration
import scala.math.Ordering.Double.TotalOrdering

case class DayVal(day: Int) extends AnyVal with WrappedInt {
  override def value = day
}

object DayVal extends JsonCompanion[Int, DayVal] {
  override def write(t: DayVal) = t.day
}

case class MonthVal(month: Int) extends AnyVal with WrappedInt {
  override def value = month
}

object MonthVal extends JsonCompanion[Int, MonthVal] {
  override def write(t: MonthVal) = t.month
}

case class YearVal(year: Int) extends AnyVal with WrappedInt {
  override def value = year
}

object YearVal extends JsonCompanion[Int, YearVal] {
  override def write(t: YearVal) = t.year
}

trait WrappedInt extends Any {
  def value: Int
  override def toString = s"$value"
}

case class Bearing(bearing: Int) extends AnyVal

object Bearing {
  val north = apply(0)
  val east = apply(90)
  val south = apply(180)
  val west = apply(270)
}

case class FormattedTime(time: String) extends AnyVal with WrappedString {
  override def value = time
}

object FormattedTime extends StringCompanion[FormattedTime]

case class FormattedDate(date: String) extends AnyVal with WrappedString {
  override def value = date
}

object FormattedDate extends StringCompanion[FormattedDate]

case class FormattedDateTime(dateTime: String) extends AnyVal with WrappedString {
  override def value = dateTime
}

object FormattedDateTime extends StringCompanion[FormattedDateTime]

case class Timing(
  date: FormattedDate,
  time: FormattedTime,
  dateTime: FormattedDateTime,
  millis: Long
)

object Timing {
  implicit val json = Json.format[Timing]
}

case class Times(start: Timing, end: Timing, range: String)

object Times {
  implicit val json = Json.format[Times]
}

trait Degree

/** Latitude in decimal degrees.
  *
  * @param lat latitude aka y
  */
case class Latitude(lat: Double) extends AnyVal {
  override def toString = s"$lat"
}

object Latitude extends ValidatedDouble[Latitude] {
  override def build(input: Double): Either[ErrorMessage, Latitude] =
    if (input >= -90 && input <= 90) Right(apply(input))
    else Left(ErrorMessage(s"Invalid latitude: '$input'. Must be between -90 and 90."))

  override def write(t: Latitude): Double = t.lat
}

/** Longitude in decimal degrees.
  *
  * @param lng longitude aka x
  */
case class Longitude(lng: Double) extends AnyVal {
  override def toString = s"$lng"
}

object Longitude extends ValidatedDouble[Longitude] {
  override def build(input: Double): Either[ErrorMessage, Longitude] =
    if (input >= -180 && input <= 180) Right(apply(input))
    else Left(ErrorMessage(s"Invalid longitude: '$input'. Must be between -180 and 180."))

  override def write(t: Longitude): Double = t.lng
}

case class CoordHash(hash: String) extends AnyVal {
  override def toString: String = hash
}

case class Coord(lng: Longitude, lat: Latitude) {
  override def toString = s"($lng, $lat)"

  def toArray: Array[Double] = Array(lng.lng, lat.lat)

  def approx: String = {
    val lngStr = Coord.format(lng.lng)
    val latStr = Coord.format(lat.lat)
    s"$lngStr,$latStr"
  }

  val hash = CoordHash(approx)
}

object Coord {
  val Key = "coord"

  implicit val json: OFormat[Coord] = Json.format[Coord]
  // GeoJSON format
  val jsonArray = Format(
    Reads[Coord] { json =>
      json.validate[List[Double]].flatMap {
        case lng :: lat :: _ =>
          build(lng, lat).fold(err => JsError(err.message), c => JsSuccess(c))
        case _ =>
          JsError(
            s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$json'."
          )
      }
    },
    Writes[Coord] { c =>
      Json.toJson(c.toArray)
    }
  )

  def buildOrFail(lng: Double, lat: Double): Coord =
    build(lng, lat).fold(err => throw new Exception(err.message), identity)

  def build(lng: Double, lat: Double): Either[ErrorMessage, Coord] =
    for {
      longitude <- Longitude.build(lng)
      latitude <- Latitude.build(lat)
    } yield Coord(longitude, latitude)

  def format(d: Double): String = {
    val trunc = (d * 100000).toInt.toDouble / 100000
    "%1.5f".format(trunc)
  }
}

case class RouteRequest(from: Coord, to: Coord)

object RouteRequest {
  implicit val json = Json.format[RouteRequest]

  def apply(
    srcLat: Double,
    srcLng: Double,
    destLat: Double,
    destLng: Double
  ): Either[ErrorMessage, RouteRequest] =
    for {
      from <- Coord.build(srcLng, srcLat)
      to <- Coord.build(destLng, destLat)
    } yield RouteRequest(from, to)
}

trait MeasuredCoord {
  def coord: Coord
  def speed: SpeedM
}

case class SimpleCoord(coord: Coord, speed: SpeedM) extends MeasuredCoord

case class TimedCoord(
  id: TrackPointId,
  coord: Coord,
  boatTime: FormattedDateTime,
  boatTimeMillis: Long,
  boatTimeOnly: FormattedTime,
  speed: SpeedM,
  waterTemp: Temperature,
  depthMeters: DistanceM,
  time: Timing
) extends MeasuredCoord {
  def lng = coord.lng
  def lat = coord.lat
}

object TimedCoord {
  val SpeedKey = "speed"
  val DepthKey = "depth"
  val modern = Json.format[TimedCoord]
  // For backwards compat
  implicit val json = Format[TimedCoord](
    modern,
    Writes(tc => modern.writes(tc) ++ Json.obj("depth" -> tc.depthMeters.toMillis.toLong))
  )
}

case class GPSTimedCoord(id: GPSPointId, coord: Coord, time: Timing)

object GPSTimedCoord {
  implicit val json = Json.format[GPSTimedCoord]
}

case class AccessToken(token: String) extends AnyVal with WrappedString {
  override def value = token
}

object AccessToken extends StringCompanion[AccessToken]

case class BoatName(name: String) extends AnyVal with WrappedString {
  override def value = name
}

object BoatName extends StringCompanion[BoatName] {
  val Key = "boatName"
}

case class TrackName(name: String) extends AnyVal with WrappedString {
  override def value = name
}

object TrackName extends StringCompanion[TrackName] {
  val Key = "track"
}

case class TrackTitle(title: String) extends AnyVal with WrappedString {
  override def value = title
}

object TrackTitle extends StringCompanion[TrackTitle] {
  val Key = "title"
  val MaxLength = 191
}

case class ChangeTrackTitle(title: TrackTitle)

object ChangeTrackTitle {
  implicit val json = Json.format[ChangeTrackTitle]
}

object TrackComments {
  val Key = "comments"
}

case class ChangeComments(comments: String)

object ChangeComments {
  implicit val json = Json.format[ChangeComments]
}

case class TrackCanonical(name: String) extends AnyVal with WrappedString {
  override def value = name
}

object TrackCanonical extends StringCompanion[TrackCanonical] {
  val Key = "canonical"

  def fromName(name: TrackName): TrackCanonical = TrackCanonical(name.name)
}

case class BoatToken(token: String) extends AnyVal with WrappedString {
  override def value = token
}

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
case class RawSentence(sentence: String) extends AnyVal with WrappedString {
  override def value = sentence
}

object RawSentence extends StringCompanion[RawSentence] {
  val MaxLength = 82
  val initialZda = RawSentence("$GPZDA,,00,00,0000,-03,00*66")
}

object Usernames {
  val anon = Username("anon")
}

case class Language(code: String) extends AnyVal with WrappedString {
  override def value = code
}

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

case class SimpleMessage(message: String) extends AnyVal

object SimpleMessage {
  implicit val json = Json.format[SimpleMessage]
}

trait BoatTrackMeta extends DeviceMeta {
  def track: TrackName
}

trait UserDevice {
  def userId: UserId
  def deviceName: BoatName
}

trait DeviceMeta {
  def user: Username
  def boat: BoatName
  def withTrack(track: TrackName) = BoatUser(track, boat, user)
  def withDevice(id: DeviceId) = IdentifiedDevice(user, boat, id)
}

trait IdentifiedDeviceMeta extends DeviceMeta {
  def device: DeviceId
}

case class SimpleBoatMeta(user: Username, boat: BoatName) extends DeviceMeta

case class IdentifiedDevice(user: Username, boat: BoatName, device: DeviceId)
  extends IdentifiedDeviceMeta

case class DeviceId(id: Long) extends AnyVal with WrappedId
object DeviceId extends IdCompanion[DeviceId] {
  val Key = "boat"
}

case class TrackId(id: Long) extends AnyVal with WrappedId
object TrackId extends IdCompanion[TrackId]

case class PushId(id: Long) extends AnyVal with WrappedId
object PushId extends IdCompanion[PushId]

case class PushToken(token: String) extends AnyVal with WrappedString {
  override def value = token
}

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

case class Boat(id: DeviceId, name: BoatName, token: BoatToken, addedMillis: Long)

object Boat {
  implicit val json = Json.format[Boat]
}

trait MinimalUserInfo {
  def username: Username
  def language: Language
  def authorized: Seq[BoatName]
}

object MinimalUserInfo {
  def anon: MinimalUserInfo = SimpleUserInfo(Usernames.anon, Language.default, Nil)
}

case class SimpleUserInfo(username: Username, language: Language, authorized: Seq[BoatName])
  extends MinimalUserInfo

trait EmailUser extends MinimalUserInfo {
  def email: Email
}

case class SimpleEmailUser(
  username: Username,
  email: Email,
  language: Language,
  authorized: Seq[BoatName]
) extends EmailUser

case class BoatRef(id: DeviceId, name: BoatName)

object BoatRef {
  implicit val json = Json.format[BoatRef]
}

case class Invite(boat: BoatRef, state: InviteState, addedMillis: Long)

case class FriendRef(id: UserId, email: Email)

object FriendRef {
  implicit val json = Json.format[FriendRef]
}

case class FriendInvite(
  boat: BoatRef,
  friend: FriendRef,
  state: InviteState,
  addedMillis: Long
)

object FriendInvite {
  implicit val json = Json.format[FriendInvite]
}

object Invite {
  implicit val json = Json.format[Invite]
}

case class UserInfo(
  id: UserId,
  username: Username,
  email: Email,
  language: Language,
  boats: Seq[Boat],
  enabled: Boolean,
  addedMillis: Long,
  invites: Seq[Invite],
  friends: Seq[FriendInvite]
) extends EmailUser {
  override val authorized: Seq[BoatName] = boats.map(_.name) ++ invites.map(_.boat.name)
}

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

case class TrackMetaShort(
  track: TrackId,
  trackName: TrackName,
  boat: DeviceId,
  boatName: BoatName,
  username: Username
) extends TrackMetaLike

object TrackMetaShort {
  implicit val json = Json.format[TrackMetaShort]
}

case class DeviceRef(device: DeviceId, deviceName: BoatName, username: Username)

object DeviceRef {
  implicit val json = Json.format[DeviceRef]
}

case class TrackRef(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  canonical: TrackCanonical,
  comments: Option[String],
  boat: DeviceId,
  boatName: BoatName,
  username: Username,
  points: Int,
  duration: Duration,
  distanceMeters: DistanceM,
  topSpeed: Option[SpeedM],
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  topPoint: TimedCoord,
  times: Times
) extends TrackLike {
  def describe = trackTitle.map(_.title).getOrElse(trackName.name)
}

object TrackRef {
  implicit val durationFormat = PrimitiveFormats.durationFormat
  val modern = Json.format[TrackRef]
  implicit val json = Format[TrackRef](
    modern,
    Writes(tr => modern.writes(tr) ++ Json.obj("distance" -> tr.distanceMeters.toMillis.toLong))
  )
}

case class TrackResponse(track: TrackRef)

object TrackResponse {
  implicit val json = Json.format[TrackResponse]
}

case class InsertedTrackPoint(point: TrackPointId, track: TrackRef)

case class TrackPointId(id: Long) extends AnyVal with WrappedId

object TrackPointId extends IdCompanion[TrackPointId]

case class GPSPointId(id: Long) extends AnyVal with WrappedId

object GPSPointId extends IdCompanion[GPSPointId]

case class BoatUser(track: TrackName, boat: BoatName, user: Username) extends BoatTrackMeta

case class BoatInfo(
  boatId: DeviceId,
  boat: BoatName,
  user: Username,
  language: Language,
  tracks: Seq[TrackRef]
)

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

case class GPSCoordsEvent(coords: List[GPSTimedCoord], from: DeviceRef) extends DeviceFrontEvent

object GPSCoordsEvent {
  val Key = "gps-coords"
  implicit val json = keyValued(Key, Json.format[GPSCoordsEvent])
}

case class CoordsEvent(coords: List[TimedCoord], from: TrackRef) extends BoatFrontEvent {
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
  override def isIntendedFor(user: MinimalUserInfo): Boolean =
    events.forall(_.isIntendedFor(user))
}

object CoordsBatch {
  implicit val json = Json.format[CoordsBatch]
}

case class GPSSentencesEvent(sentences: Seq[RawSentence], from: IdentifiedDeviceMeta) {
  def length: Int = sentences.length
}

case class SentencesEvent(sentences: Seq[RawSentence], from: TrackMetaShort) extends BoatFrontEvent

object SentencesEvent {
  val Key = "sentences"
  implicit val json = keyValued(Key, Json.format[SentencesEvent])
}

case class PingEvent(sent: Long) extends FrontEvent {
  override def isIntendedFor(user: MinimalUserInfo) = true
}

object PingEvent {
  val Key = "ping"
  implicit val json = keyValued(Key, Json.format[PingEvent])
}

case class VesselMessages(vessels: Seq[VesselInfo]) extends FrontEvent {
  override def isIntendedFor(user: MinimalUserInfo): Boolean = true
}

object VesselMessages {
  val Key = "vessels"
  implicit val json = keyValued(Key, Json.format[VesselMessages])
  val empty = VesselMessages(Nil)
}

sealed trait FrontEvent {
  def isIntendedFor(user: MinimalUserInfo): Boolean
}

sealed trait DeviceFrontEvent extends FrontEvent {
  def from: DeviceRef

  override def isIntendedFor(user: MinimalUserInfo): Boolean =
    user.username == from.username || user.authorized.contains(from.deviceName)
}

sealed trait BoatFrontEvent extends FrontEvent {
  def from: TrackMetaLike

  override def isIntendedFor(user: MinimalUserInfo): Boolean =
    user.username == from.username || user.authorized.contains(from.boatName)
}

object FrontEvent {
  implicit val reader = Reads[FrontEvent] { json =>
    VesselMessages.json
      .reads(json)
      .orElse(CoordsEvent.json.reads(json))
      .orElse(CoordsBatch.json.reads(json))
      .orElse(SentencesEvent.json.reads(json))
      .orElse(PingEvent.json.reads(json))
      .orElse(GPSCoordsEvent.json.reads(json))
  }
  implicit val writer = Writes[FrontEvent] {
    case se @ SentencesEvent(_, _)  => SentencesEvent.json.writes(se)
    case ce @ CoordsEvent(_, _)     => CoordsEvent.json.writes(ce)
    case cb @ CoordsBatch(_)        => CoordsBatch.json.writes(cb)
    case pe @ PingEvent(_)          => PingEvent.json.writes(pe)
    case vs @ VesselMessages(_)     => VesselMessages.json.writes(vs)
    case gce @ GPSCoordsEvent(_, _) => GPSCoordsEvent.json.writes(gce)
  }
}

case class SentencesMessage(sentences: Seq[RawSentence]) {
  def toTrackEvent(from: TrackMetaShort) = SentencesEvent(sentences, from)
  def toGpsEvent(from: IdentifiedDeviceMeta) = GPSSentencesEvent(sentences, from)
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

case class IconsConf(boat: String, trophy: String)

object IconsConf {
  implicit val json = Json.format[IconsConf]
}

case class MapConf(styleId: String, styleUrl: String, icons: IconsConf)

object MapConf {
  implicit val json = Json.format[MapConf]
  val active = apply(Constants.StyleId)

  def apply(styleId: String): MapConf = MapConf(
    styleId,
    s"mapbox://styles/malliina/$styleId",
    IconsConf("boat-resized-opt-30", "trophy-gold-path")
  )
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

abstract class ValidatedDouble[T](implicit f: Format[Double])
  extends ValidatingCompanion[Double, T]()(f, TotalOrdering)

class ModelHtml[Builder, Output <: FragT, FragT](val bundle: Bundle[Builder, Output, FragT]) {

  import bundle.all._

  implicit def wrappedFrag[T <: WrappedString](t: T): Frag = stringFrag(t.value)
}

sealed abstract class InviteState(val name: String)

object InviteState extends StringEnumCompanion[InviteState] {
  val awaiting: InviteState = Awaiting
  val accepted: InviteState = Accepted
  case object Awaiting extends InviteState("awaiting")
  case object Accepted extends InviteState("accepted")
  case object Rejected extends InviteState("rejected")
  case class Other(n: String) extends InviteState(n)

  def orOther(in: String): InviteState = build(in).getOrElse(Other(in))

  override def all = Seq(Awaiting, Accepted, Rejected)
  override def write(t: InviteState) = t.name
}

object Emails {
  val Key = "email"
}

object BoatIds {
  val Key = "boat"
}

object Forms {
  val Accept = "accept"
  val Boat = BoatIds.Key
  val Email = Emails.Key
  val User = "user"
}
