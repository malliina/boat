package com.malliina.boat

import cats.syntax.all.toFunctorOps
import com.malliina.boat.BoatJson.keyValued
import com.malliina.json.PrimitiveFormats
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.*
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import scalatags.generic.Bundle

import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.math.Ordering.Double.TotalOrdering

case class DayVal(day: Int) extends AnyVal with WrappedInt:
  override def value = day

object DayVal extends JsonCompanion[Int, DayVal]:
  override def write(t: DayVal) = t.day

case class MonthVal(month: Int) extends AnyVal with WrappedInt:
  override def value = month

object MonthVal extends JsonCompanion[Int, MonthVal]:
  override def write(t: MonthVal) = t.month

case class YearVal(year: Int) extends AnyVal with WrappedInt:
  override def value = year

object YearVal extends JsonCompanion[Int, YearVal]:
  override def write(t: YearVal) = t.year

trait WrappedInt extends Any:
  def value: Int
  override def toString = s"$value"

case class Bearing(bearing: Int) extends AnyVal

object Bearing:
  val north = apply(0)
  val east = apply(90)
  val south = apply(180)
  val west = apply(270)

case class FormattedTime(time: String) extends AnyVal with WrappedString:
  override def value = time

object FormattedTime extends StringCompanion[FormattedTime]

case class FormattedDate(date: String) extends AnyVal with WrappedString:
  override def value = date

object FormattedDate extends StringCompanion[FormattedDate]

case class FormattedDateTime(dateTime: String) extends AnyVal with WrappedString:
  override def value = dateTime

object FormattedDateTime extends StringCompanion[FormattedDateTime]

case class Timing(
  date: FormattedDate,
  time: FormattedTime,
  dateTime: FormattedDateTime,
  millis: Long
) derives Codec.AsObject

case class Times(start: Timing, end: Timing, range: String) derives Codec.AsObject

/** Latitude in decimal degrees.
  *
  * @param lat
  *   latitude aka y
  */
opaque type Latitude = Double
object Latitude extends ValidatedDouble[Latitude]:
  override def build(input: Double): Either[ErrorMessage, Latitude] =
    if input >= -90 && input <= 90 then Right(input)
    else Left(ErrorMessage(s"Invalid latitude: '$input'. Must be between -90 and 90."))
  override def write(t: Latitude): Double = t
  def unsafe(d: Double): Latitude = d
  extension (lat: Latitude) def lat: Double = lat

/** Longitude in decimal degrees.
  *
  * @param lng
  *   longitude aka x
  */
opaque type Longitude = Double
object Longitude extends ValidatedDouble[Longitude]:
  override def build(input: Double): Either[ErrorMessage, Longitude] =
    if input >= -180 && input <= 180 then Right(input)
    else Left(ErrorMessage(s"Invalid longitude: '$input'. Must be between -180 and 180."))
  override def write(t: Longitude): Double = t
  def unsafe(d: Double): Longitude = d
  extension (lng: Longitude) def lng: Double = lng

case class CoordHash(hash: String) extends AnyVal:
  override def toString: String = hash

case class Coord(lng: Longitude, lat: Latitude):
  override def toString = s"($lng, $lat)"

  def toArray: Array[Double] = Array(lng.lng, lat.lat)

  def approx: String =
    val lngStr = Coord.format(lng.lng)
    val latStr = Coord.format(lat.lat)
    s"$lngStr,$latStr"

  val hash = CoordHash(approx)

object Coord:
  val Key = "coord"

  given json: Codec[Coord] = deriveCodec[Coord]
  // GeoJSON format
  val jsonArray: Codec[Coord] = Codec.from(
    Decoder[List[Double]].emap {
      case lng :: lat :: _ =>
        build(lng, lat).left.map(_.message)
      case other =>
        Left(
          s"Expected a JSON array of at least two numbers for coordinates [lng, lat]. Got: '$other'."
        )
    },
    (c: Coord) => c.toArray.toList.asJson
  )

  def buildOrFail(lng: Double, lat: Double): Coord =
    build(lng, lat).fold(err => throw new Exception(err.message), identity)

  def build(lng: Double, lat: Double): Either[ErrorMessage, Coord] =
    for
      longitude <- Longitude.build(lng)
      latitude <- Latitude.build(lat)
    yield Coord(longitude, latitude)

  def format(d: Double): String =
    val trunc = (d * 100000).toInt.toDouble / 100000
    "%1.5f".format(trunc).replace(',', '.')

case class RouteRequest(from: Coord, to: Coord) derives Codec.AsObject

object RouteRequest:
  def apply(
    srcLat: Double,
    srcLng: Double,
    destLat: Double,
    destLng: Double
  ): Either[ErrorMessage, RouteRequest] =
    for
      from <- Coord.build(srcLng, srcLat)
      to <- Coord.build(destLng, destLat)
    yield RouteRequest(from, to)

trait MeasuredCoord:
  def coord: Coord
  def speed: SpeedM

case class SimpleCoord(coord: Coord, speed: SpeedM) extends MeasuredCoord

case class TimedCoord(
  id: TrackPointId,
  coord: Coord,
  boatTime: FormattedDateTime,
  boatTimeMillis: Long,
  boatTimeOnly: FormattedTime,
  speed: SpeedM,
  altitude: Option[DistanceM],
  outsideTemp: Option[Temperature],
  waterTemp: Temperature,
  depthMeters: DistanceM,
  time: Timing
) extends MeasuredCoord:
  def lng = coord.lng
  def lat = coord.lat

object TimedCoord:
  val SpeedKey = "speed"
  private val DepthKey = "depth"
  val modern: Codec[TimedCoord] = deriveCodec[TimedCoord]
  // For backwards compat
  given Codec[TimedCoord] = Codec.from(
    modern,
    (tc: TimedCoord) =>
      modern(tc).deepMerge(Json.obj(DepthKey -> tc.depthMeters.toMillis.toLong.asJson))
  )

case class AccessToken(token: String) extends AnyVal with WrappedString:
  override def value = token

object AccessToken extends StringCompanion[AccessToken]

case class ChangeTrackTitle(title: TrackTitle) derives Codec.AsObject

object TrackComments:
  val Key = "comments"

case class ChangeComments(comments: String) derives Codec.AsObject

/** An NMEA Sentence.
  *
  * "An NMEA sentence consists of a start delimiter, followed by a comma-separated sequence of
  * fields, followed by the character * (ASCII 42), the checksum and an end-of-line marker."
  *
  * "The first field of a sentence is called the "tag" and normally consists of a two-letter talker
  * ID followed by a three-letter type code."
  *
  * "Sentences are terminated by a CRLF sequence."
  *
  * "Maximum sentence length, including the $ and CRLF is 82 bytes."
  *
  * @param sentence
  *   sentence as a String
  * @see
  *   http://www.catb.org/gpsd/NMEA.html
  */
opaque type RawSentence = String
object RawSentence extends ShowableString[RawSentence]:
  val MaxLength = 82
  val initialZda = RawSentence("$GPZDA,,00,00,0000,-03,00*66")
  override def apply(raw: String): RawSentence = raw
  override def write(t: RawSentence): String = t

object Usernames:
  val anon = Username("anon")

case class Language(code: String) extends AnyVal with WrappedString:
  override def value = code

object Language extends StringCompanion[Language]:
  val english: Language = Language("en-US")
  val finnish = Language("fi-FI")
  val swedish = Language("sv-SE")
  val default = finnish

case class ChangeLanguage(language: Language) derives Codec.AsObject

case class ChangeBoatName(boatName: BoatName) derives Codec.AsObject

case class SimpleMessage(message: String) derives Codec.AsObject

trait SourceTrackMeta extends DeviceMeta:
  def track: TrackName

trait UserDevice:
  def userId: UserId
  def device: DeviceId
  def deviceName: BoatName
  def sourceType: SourceType

trait DeviceMeta:
  def user: Username
  def boat: BoatName
  def sourceType: SourceType
  def withTrack(track: TrackName) = BoatUser(track, boat, sourceType, user)

trait IdentifiedDeviceMeta extends DeviceMeta:
  def device: DeviceId

case class SimpleSourceMeta(user: Username, boat: BoatName, sourceType: SourceType)
  extends DeviceMeta

sealed abstract class MobileDevice(val name: String):
  override def toString: String = name

object MobileDevice extends ValidatingCompanion[String, MobileDevice]:
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

case class Boat(
  id: DeviceId,
  name: BoatName,
  sourceType: SourceType,
  token: BoatToken,
  addedMillis: Long
) derives Codec.AsObject

trait MinimalUserInfo:
  def username: Username
  def language: Language
  def authorized: Seq[BoatName]

object MinimalUserInfo:
  def anon: MinimalUserInfo = SimpleUserInfo(Usernames.anon, Language.default, Nil)

case class SimpleUserInfo(username: Username, language: Language, authorized: Seq[BoatName])
  extends MinimalUserInfo

trait EmailUser extends MinimalUserInfo:
  def email: Email

case class BoatRef(id: DeviceId, name: BoatName) derives Codec.AsObject

case class Invite(boat: BoatRef, state: InviteState, addedMillis: Long) derives Codec.AsObject

case class FriendRef(id: UserId, email: Email) derives Codec.AsObject

case class FriendInvite(
  boat: BoatRef,
  friend: FriendRef,
  state: InviteState,
  addedMillis: Long
) derives Codec.AsObject

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
) extends EmailUser
  derives Codec.AsObject:
  override val authorized: Seq[BoatName] = boats.map(_.name) ++ invites.map(_.boat.name)
  def userBoats = UserBoats(username, language, Nil) // Nil is wrong, but fine for now

case class UserContainer(user: UserInfo) derives Codec.AsObject

trait TrackMetaLike:
  def boatName: BoatName
  def trackName: TrackName
  def username: Username

trait TrackLike extends TrackMetaLike:
  def duration: Duration

case class TrackMetaShort(
  track: TrackId,
  trackName: TrackName,
  boat: DeviceId,
  boatName: BoatName,
  username: Username
) extends TrackMetaLike
  derives Codec.AsObject

case class DeviceRef(device: DeviceId, deviceName: BoatName, username: Username)
  derives Codec.AsObject

case class TrackRef(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  canonical: TrackCanonical,
  comments: Option[String],
  boat: DeviceId,
  boatName: BoatName,
  sourceType: SourceType,
  username: Username,
  points: Int,
  duration: Duration,
  distanceMeters: DistanceM,
  topSpeed: Option[SpeedM],
  avgSpeed: Option[SpeedM],
  avgWaterTemp: Option[Temperature],
  avgOutsideTemp: Option[Temperature],
  topPoint: TimedCoord,
  times: Times
) extends TrackLike:
  def describe = trackTitle.map(TrackTitle.write).getOrElse(TrackName.write(trackName))

object TrackRef:
  given Codec[Duration] = PrimitiveFormats.durationCodec
  val modern: Codec[TrackRef] = deriveCodec[TrackRef]
  given Codec[TrackRef] = Codec.from(
    modern,
    (tr: TrackRef) =>
      modern(tr).deepMerge(Json.obj("distance" -> tr.distanceMeters.toMillis.toLong.asJson))
  )

case class TrackResponse(track: TrackRef) derives Codec.AsObject

case class InsertedTrackPoint(point: TrackPointId, track: TrackRef)

case class BoatUser(track: TrackName, boat: BoatName, sourceType: SourceType, user: Username)
  extends SourceTrackMeta

case class BoatInfo(
  boatId: DeviceId,
  boat: BoatName,
  user: Username,
  language: Language,
  tracks: Seq[TrackRef]
) derives Codec.AsObject

case class UserBoats(user: Username, language: Language, boats: Seq[BoatInfo])

object UserBoats:
  val anon = UserBoats(Usernames.anon, Language.default, Nil)

case class TrackSummary(track: TrackRef) derives Codec.AsObject

case class TrackSummaries(tracks: Seq[TrackSummary]) derives Codec.AsObject

case class Tracks(tracks: Seq[TrackRef]) derives Codec.AsObject

case class CoordsEvent(coords: List[TimedCoord], from: TrackRef) extends BoatFrontEvent:
  def isEmpty = coords.isEmpty
  def sample(every: Int): CoordsEvent =
    if every <= 1 then this
    else copy(coords = coords.grouped(every).flatMap(_.headOption).toList, from)
  def addCoords(newCoords: Seq[TimedCoord]) = copy(coords = coords ++ newCoords)
  def toMap: Map[String, Json] = coords.map(tc => tc.id.toString -> tc.asJson).toMap

object CoordsEvent:
  val Key = "coords"
  implicit val coordsJson: Codec[Coord] = Coord.json
  implicit val json: Codec[CoordsEvent] = keyValued(Key, deriveCodec[CoordsEvent])

// Watt hours
opaque type Energy = Double
object Energy extends JsonCompanion[Double, Energy]:
  override def apply(raw: Double): Energy = raw
  override def write(t: Energy): Double = t
extension (e: Energy)
  def wattHours: Double = e
  def -(other: Energy): Energy = Energy(e.wattHours - other.wattHours)
extension (e: Double) def wh: Energy = Energy(e)

case class CarInfo(id: DeviceId, name: BoatName, username: Username) derives Codec.AsObject

case class CoordsBatch(events: Seq[CoordsEvent]) extends FrontEvent derives Codec.AsObject:
  override def isIntendedFor(user: MinimalUserInfo): Boolean =
    events.forall(_.isIntendedFor(user))

case class SentencesEvent(sentences: Seq[RawSentence], from: TrackMetaShort) extends BoatFrontEvent

object SentencesEvent:
  val Key = "sentences"
  given Codec[SentencesEvent] = keyValued(Key, deriveCodec[SentencesEvent])

case class PingEvent(sent: Long, age: Duration) extends FrontEvent:
  override def isIntendedFor(user: MinimalUserInfo) = true

object PingEvent:
  val Key = "ping"
  given Codec[Duration] = PrimitiveFormats.durationCodec
  given Codec[PingEvent] = keyValued(Key, deriveCodec[PingEvent])

case class VesselMessages(vessels: Seq[VesselInfo]) extends FrontEvent:
  override def isIntendedFor(user: MinimalUserInfo): Boolean = true

object VesselMessages:
  val Key = "vessels"
  given Codec[VesselMessages] = keyValued(Key, deriveCodec[VesselMessages])
  val empty = VesselMessages(Nil)

sealed trait BoatFrontEvent extends FrontEvent:
  def from: TrackMetaLike
  override def isIntendedFor(user: MinimalUserInfo): Boolean =
    user.username == from.username || user.authorized.contains(from.boatName)

sealed trait FrontEvent:
  def isIntendedFor(user: MinimalUserInfo): Boolean

object FrontEvent:
  given Decoder[FrontEvent] = List[Decoder[FrontEvent]](
    Decoder[VesselMessages].widen,
    Decoder[CoordsBatch].widen,
    Decoder[SentencesEvent].widen,
    Decoder[PingEvent].widen,
    Decoder[CoordsEvent].widen
  ).reduceLeft(_ or _)
  given Encoder[FrontEvent] = {
    case se @ SentencesEvent(_, _) => se.asJson
    case ce @ CoordsEvent(_, _)    => ce.asJson
    case cb @ CoordsBatch(_)       => cb.asJson
    case pe @ PingEvent(_, _)      => pe.asJson
    case vs @ VesselMessages(_)    => vs.asJson
  }

case class SentencesMessage(sentences: Seq[RawSentence]):
  def toTrackEvent(from: TrackMetaShort) = SentencesEvent(sentences, from)

object SentencesMessage:
  val Key = "sentences"
  given Codec[SentencesMessage] = keyValued(Key, deriveCodec[SentencesMessage])

object BoatJson:
  val EventKey = "event"
  private val BodyKey = "body"

  private val eventDecoder = Decoder.decodeString.at(EventKey)

  /** A JSON format for objects of type T that contains a top-level "event" key and further data in
    * "body".
    */
  def keyValued[T](value: String, payload: Codec[T]): Codec[T] =
    val decoder = eventDecoder.flatMap[T]: event =>
      if event == value then payload.at(BodyKey)
      else Decoder.failed(DecodingFailure(s"Event is '$event', required '$value'.", Nil))
    val encoder: Encoder[T] = (a: T) =>
      Json.obj(
        (EventKey, Json.fromString(value)),
        (BodyKey, payload(a))
      )
    Codec.from(decoder, encoder)

case class IconsConf(boat: String, trophy: String) derives Codec.AsObject

case class MapConf(styleId: String, styleUrl: String, icons: IconsConf) derives Codec.AsObject

object MapConf:
  val old = apply(Constants.StyleIdOld)
  val active = apply(Constants.StyleId)

  def apply(styleId: String): MapConf = MapConf(
    styleId,
    s"mapbox://styles/skogberglabs/$styleId",
    IconsConf("boat-resized-opt-30", "trophy-gold-path")
  )

abstract class ValidatedDouble[T](implicit d: Decoder[Double], e: Encoder[Double])
  extends ValidatingCompanion[Double, T]()(d, e, TotalOrdering):
  extension (t: T) def value: Double = write(t)
  def fromString(s: String) =
    s.toDoubleOption.toRight(ErrorMessage(s"Not a double: '$s'.")).flatMap(build)

class ModelHtml[Builder, Output <: FragT, FragT](val bundle: Bundle[Builder, Output, FragT]):
  import bundle.all.{Frag, stringFrag}

  implicit def wrappedFrag[T <: WrappedString](t: T): Frag = stringFrag(t.value)

sealed abstract class SourceType(val name: String)
object SourceType extends StringEnumCompanion[SourceType]:
  case object Vehicle extends SourceType("vehicle")
  case object Boat extends SourceType("boat")
  case class Other(n: String) extends SourceType(n)
  def orOther(in: String): SourceType = build(in).getOrElse(Other(in))
  override def all = Seq(Vehicle, Boat)
  override def write(t: SourceType) = t.name

sealed abstract class InviteState(val name: String)

object InviteState extends StringEnumCompanion[InviteState]:
  val awaiting: InviteState = Awaiting
  val accepted: InviteState = Accepted
  case object Awaiting extends InviteState("awaiting")
  case object Accepted extends InviteState("accepted")
  case object Rejected extends InviteState("rejected")
  case class Other(n: String) extends InviteState(n)

  def orOther(in: String): InviteState = build(in).getOrElse(Other(in))

  override def all = Seq(Awaiting, Accepted, Rejected)
  override def write(t: InviteState) = t.name

object Emails:
  val Key = "email"

object BoatIds:
  val Key = "boat"

object Forms:
  val Accept = "accept"
  val Boat = BoatIds.Key
  val Email = Emails.Key
  val User = "user"

object Timings:
  val From = "from"
  val To = "to"
