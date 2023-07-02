package com.malliina.boat.db

import com.malliina.boat.db.Values.VesselUpdateId

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{AisUpdateId, BoatName, BoatToken, CarUpdateId, Coord, CoordHash, DateVal, DeviceId, Energy, FairwayLighting, GPSPointId, GPSSentenceKey, InviteState, Language, Latitude, Longitude, MobileDevice, MonthVal, PushId, PushToken, RawSentence, SeaArea, SentenceKey, SourceType, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, UserToken, VesselRowId, YearVal, long}
import com.malliina.measure.{DistanceM, SpeedDoubleM, SpeedM, Temperature}
import com.malliina.values.*
import com.vividsolutions.jts.geom.Point
import doobie.*

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object Mappings extends Mappings

trait Mappings:
  implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val odtMeta: Meta[OffsetDateTime] =
    Meta[Instant].imap(_.atOffset(ZoneOffset.UTC))(_.toInstant)
  implicit val localDateMeta: Meta[LocalDate] =
    doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  implicit val tpi: Meta[TrackPointId] = wrappedId(TrackPointId.apply)
  implicit val rti: Meta[RefreshTokenId] = simple(RefreshTokenId)
  implicit val di: Meta[DeviceId] = simple(DeviceId)
  implicit val gpi: Meta[GPSPointId] = wrappedId(GPSPointId.apply)
  implicit val gpf: Meta[GPSFix] = Meta[String].timap(GPSFix.orOther)(_.value)
  implicit val pi: Meta[PushId] = simple(PushId)
  implicit val pt: Meta[PushToken] = simple(PushToken)
  implicit val md: Meta[MobileDevice] = Meta[String].timap(MobileDevice.apply)(_.name)
  implicit val sa: Meta[SeaArea] = Meta[Int].timap(SeaArea.fromIntOrOther)(_.value)
  implicit val fl: Meta[FairwayLighting] =
    Meta[Int].timap(FairwayLighting.fromInt)(FairwayLighting.toInt)
  implicit val rs: Meta[RawSentence] = wrapped(RawSentence.apply)
  implicit val ti: Meta[TrackId] = simple(TrackId)
  implicit val vr: Meta[VesselRowId] = wrappedId(VesselRowId.apply)
  implicit val aui: Meta[AisUpdateId] = wrappedId(AisUpdateId.apply)
  implicit val ui: Meta[VesselUpdateId] = Meta[Long].timap(VesselUpdateId.apply)(_.raw)
  implicit val fi: Meta[FairwayId] = wrappedId(FairwayId.apply)
  implicit val fci: Meta[FairwayCoordId] = wrappedId(FairwayCoordId.apply)
  implicit val tn: Meta[TrackName] = simple(TrackName)
  implicit val tt: Meta[TrackTitle] = simple(TrackTitle)
  implicit val tc: Meta[TrackCanonical] = simple(TrackCanonical)
  implicit val bn: Meta[BoatName] = simple(BoatName)
  implicit val bt: Meta[BoatToken] = simple(BoatToken)
  implicit val ut: Meta[UserToken] = wrapped(UserToken.apply)
  implicit val lon: Meta[Longitude] = Meta[Double].timap(Longitude.apply)(_.lng)
  implicit val lat: Meta[Latitude] = Meta[Double].timap(Latitude.apply)(_.lat)
  implicit val speed: Meta[SpeedM] = Meta[Double].timap(_.kmh)(_.toKmh)
  implicit val uid: Meta[UserId] = wrappedId(UserId.apply)
  implicit val us: Meta[Username] = wrapped(Username.apply)
  implicit val em: Meta[Email] = wrapped(Email.apply)
  implicit val lan: Meta[Language] = wrapped(Language.apply)
  implicit val ch: Meta[CoordHash] = Meta[String].timap(CoordHash.apply)(_.hash)
  implicit val temperature: Meta[Temperature] = Meta[Double].timap(Temperature.apply)(_.celsius)
  implicit val distanceMeta: Meta[DistanceM] = Meta[Double].timap(DistanceM.apply)(_.meters)
  implicit val du: Meta[FiniteDuration] =
    Meta[Double].timap(d => d.seconds)(_.toMillis.toDouble / 1000d)
  implicit val coordMeta: Meta[Coord] = Meta[Array[Byte]].timap(bytes =>
    toCoord(SpatialUtils.fromBytes[Point](bytes))
  )(SpatialUtils.coordToBytes)
  implicit val dateMapping: Meta[DateVal] = Meta[LocalDate].timap(d => DateVal(d))(_.toLocalDate)
  implicit val year: Meta[YearVal] = Meta[Int].timap(y => YearVal(y))(_.year)
  implicit val month: Meta[MonthVal] = Meta[Int].timap(m => MonthVal(m))(_.month)
  implicit val isMapping: Meta[InviteState] =
    Meta[String].timap(s => InviteState.orOther(s))(_.name)
  implicit val st: Meta[SourceType] = Meta[String].timap(s => SourceType.orOther(s))(_.name)
  implicit val sk: Meta[SentenceKey] = wrappedId(SentenceKey.apply)
  implicit val gsk: Meta[GPSSentenceKey] = wrappedId(GPSSentenceKey.apply)
  implicit val dg: Meta[Degrees] = Meta[Float].timap(Degrees.unsafe)(_.float)
  implicit val cuid: Meta[CarUpdateId] = Meta[Long].timap(CarUpdateId.apply)(_.long)
  implicit val energyMeta: Meta[Energy] = Meta[Double].timap(Energy.apply)(Energy.write)

  private def simple[T, R: Meta, C <: JsonCompanion[R, T]](c: C): Meta[T] =
    Meta[R].timap(c.apply)(c.write)

  private def wrapped[T <: WrappedString](build: String => T): Meta[T] =
    Meta[String].timap(build)(_.value)

  private def wrappedId[T <: WrappedId](build: Long => T): Meta[T] =
    Meta[Long].timap(build)(_.id)

  private def toCoord(point: Point): Coord =
    val c = point.getCoordinate
    Coord(Longitude(c.x), Latitude(c.y))
