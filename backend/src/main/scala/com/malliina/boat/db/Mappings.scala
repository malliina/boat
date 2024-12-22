package com.malliina.boat.db

import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.db.Values.VesselUpdateId

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{AisUpdateId, BoatName, BoatToken, CarUpdateId, Coord, CoordHash, DateVal, DeviceId, Energy, FairwayLighting, InviteState, Language, Latitude, Longitude, MobileDevice, MonthVal, PushId, PushToken, RawSentence, SeaArea, SentenceKey, SourceType, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, UserToken, VesselName, VesselRowId, YearVal}
import com.malliina.measure.{DistanceM, SpeedDoubleM, SpeedM, Temperature}
import com.malliina.values.*
import com.vividsolutions.jts.geom.Point
import doobie.*
import org.typelevel.ci.CIString
import cats.syntax.show.toShow

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object Mappings extends Mappings

trait Mappings:
  given Meta[CIString] = Meta[String].timap(s => CIString(s))(_.toString)
  given Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  given Meta[OffsetDateTime] =
    Meta[Instant].imap(_.atOffset(ZoneOffset.UTC))(_.toInstant)
  given Meta[LocalDate] =
    doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  given Meta[TrackPointId] = simple(TrackPointId)
  given Meta[RefreshTokenId] = simple(RefreshTokenId)
  given Meta[DeviceId] = simple(DeviceId)
  given Meta[GPSFix] = Meta[String].timap(GPSFix.orOther)(_.value)
  given Meta[PushId] = simple(PushId)
  given Meta[PushToken] = simple(PushToken)
  given Meta[MobileDevice] = Meta[String].timap(MobileDevice.apply)(_.name)
  given Meta[SeaArea] = Meta[Int].timap(SeaArea.fromIntOrOther)(_.value)
  given Meta[FairwayLighting] =
    Meta[Int].timap(FairwayLighting.fromInt)(FairwayLighting.toInt)
  given Meta[RawSentence] = simple(RawSentence)
  given Meta[TrackId] = simple(TrackId)
  given Meta[VesselRowId] = simple(VesselRowId)
  given Meta[AisUpdateId] = simple(AisUpdateId)
  given Meta[VesselUpdateId] = Meta[Long].timap(VesselUpdateId.apply)(_.raw)
  given Meta[VesselName] = simple(VesselName)
  given Meta[FairwayId] = simple(FairwayId)
  given Meta[FairwayCoordId] = simple(FairwayCoordId)
  given Meta[TrackName] = simple(TrackName)
  given Meta[TrackTitle] = simple(TrackTitle)
  given Meta[TrackCanonical] = simple(TrackCanonical)
  given Meta[BoatName] = simple(BoatName)
  given Meta[BoatToken] = simple(BoatToken)
  given Meta[UserToken] = simple(UserToken)
  given Meta[Longitude] = Meta[Double].timap(Longitude.unsafe)(_.lng)
  given Meta[Latitude] = Meta[Double].timap(Latitude.unsafe)(_.lat)
  given Meta[SpeedM] = Meta[Double].timap(_.kmh)(_.toKmh)
  given Meta[UserId] = wrappedId(UserId.apply)
  given Meta[Username] = wrapped(Username.apply)
  given Meta[Email] = wrapped(Email.apply)
  given Meta[Language] = wrapped(Language.apply)
  given Meta[CoordHash] = Meta[String].timap(CoordHash.fromString)(_.hash)
  given Meta[Temperature] = Meta[Double].timap(Temperature.apply)(_.celsius)
  given Meta[DistanceM] = Meta[Double].timap(DistanceM.apply)(_.meters)
  given Meta[FiniteDuration] =
    Meta[Double].timap(d => d.seconds)(_.toMillis.toDouble / 1000d)
  given Meta[Coord] = Meta[Array[Byte]].timap(bytes =>
    toCoord(SpatialUtils.fromBytes[Point](bytes))
  )(SpatialUtils.coordToBytes)
  given Meta[DateVal] = Meta[LocalDate].timap(d => DateVal(d))(_.toLocalDate)
  given Meta[YearVal] = Meta[Int].timap(y => YearVal(y))(_.year)
  given Meta[MonthVal] = Meta[Int].timap(m => MonthVal(m))(_.month)
  given Meta[InviteState] =
    Meta[String].timap(s => InviteState.orOther(s))(_.name)
  given Meta[SourceType] = Meta[String].timap(s => SourceType.orOther(s))(_.name)
  given Meta[SentenceKey] = simple(SentenceKey)
  given Meta[Degrees] = Meta[Float].timap(Degrees.unsafe)(_.float)
  given Meta[CarUpdateId] = simple(CarUpdateId)
  given Meta[Energy] = simple(Energy)
  given Meta[Host] =
    Meta[String].timap(s => Host.fromString(s).getOrElse(fail(s"Invalid host: '$s'.")))(_.show)
  given Meta[Port] =
    Meta[Int].timap(i => Port.fromInt(i).getOrElse(fail(s"Invalid port: '$i'.")))(_.value)

  private def simple[T, R: Meta, C <: JsonCompanion[R, T]](c: C): Meta[T] =
    Meta[R].timap(c.apply)(c.write)

  private def wrapped[T <: WrappedString](build: String => T): Meta[T] =
    Meta[String].timap(build)(_.value)

  private def wrappedId[T <: WrappedId](build: Long => T): Meta[T] =
    Meta[Long].timap(build)(_.id)

  private def toCoord(point: Point): Coord =
    val c = point.getCoordinate
    Coord(Longitude.unsafe(c.x), Latitude.unsafe(c.y))

  private def fail(msg: String): Nothing = throw Exception(msg)
