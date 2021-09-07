package com.malliina.boat.db

import java.time.{Instant, LocalDate}
import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{BoatName, BoatToken, Coord, CoordHash, DateVal, DeviceId, FairwayLighting, GPSPointId, GPSSentenceKey, InviteState, Language, Latitude, Longitude, MobileDevice, MonthVal, PushId, PushToken, RawSentence, SeaArea, SentenceKey, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, UserToken, YearVal}
import com.malliina.measure.{DistanceM, SpeedDoubleM, SpeedM, Temperature}
import com.malliina.values._
import com.vividsolutions.jts.geom.Point
import doobie._

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object DoobieMappings extends DoobieMappings

trait DoobieMappings {
  implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val localDateMeta: Meta[LocalDate] =
    doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  implicit val tpi: Meta[TrackPointId] = wrappedId(TrackPointId.apply)
  implicit val di: Meta[DeviceId] = wrappedId(DeviceId.apply)
  implicit val gpi: Meta[GPSPointId] = wrappedId(GPSPointId.apply)
  implicit val gpf: Meta[GPSFix] = Meta[String].timap(GPSFix.orOther)(_.value)
  implicit val pi: Meta[PushId] = wrappedId(PushId.apply)
  implicit val pt: Meta[PushToken] = wrapped(PushToken.apply)
  implicit val md: Meta[MobileDevice] = Meta[String].timap(MobileDevice.apply)(_.name)
  implicit val sa: Meta[SeaArea] = Meta[Int].timap(SeaArea.fromIntOrOther)(_.value)
  implicit val fl: Meta[FairwayLighting] =
    Meta[Int].timap(FairwayLighting.fromInt)(FairwayLighting.toInt)
  implicit val rs: Meta[RawSentence] = wrapped(RawSentence.apply)
  implicit val ti: Meta[TrackId] = wrappedId(TrackId.apply)
  implicit val fi: Meta[FairwayId] = wrappedId(FairwayId.apply)
  implicit val fci: Meta[FairwayCoordId] = wrappedId(FairwayCoordId.apply)
  implicit val tn: Meta[TrackName] = wrapped(TrackName.apply)
  implicit val tt: Meta[TrackTitle] = wrapped(TrackTitle.apply)
  implicit val tc: Meta[TrackCanonical] = wrapped(TrackCanonical.apply)
  implicit val bn: Meta[BoatName] = wrapped(BoatName.apply)
  implicit val bt: Meta[BoatToken] = wrapped(BoatToken.apply)
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
  implicit val sk: Meta[SentenceKey] = wrappedId(SentenceKey.apply)
  implicit val gsk: Meta[GPSSentenceKey] = wrappedId(GPSSentenceKey.apply)

  def wrapped[T <: WrappedString](build: String => T): Meta[T] =
    Meta[String].timap(build)(_.value)

  def wrappedId[T <: WrappedId](build: Long => T): Meta[T] =
    Meta[Long].timap(build)(_.id)

  private def toCoord(point: Point): Coord = {
    val c = point.getCoordinate
    Coord(Longitude(c.x), Latitude(c.y))
  }
}
