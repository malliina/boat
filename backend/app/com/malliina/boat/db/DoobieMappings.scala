package com.malliina.boat.db

import java.time.{Instant, LocalDate}

import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{BoatName, BoatToken, Coord, CoordHash, DateVal, DeviceId, FairwayLighting, GPSPointId, GPSSentenceKey, Language, Latitude, Longitude, MobileDevice, MonthVal, PushId, PushToken, RawSentence, SeaArea, SentenceKey, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, UserToken, YearVal}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import com.vividsolutions.jts.geom.Point
import doobie._

import scala.concurrent.duration.FiniteDuration
import concurrent.duration.DurationDouble

object DoobieMappings extends DoobieMappings

trait DoobieMappings {
  implicit val instantMeta: Meta[Instant] = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val localDateMeta: Meta[LocalDate] =
    doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  implicit val tpi: Meta[TrackPointId] = Meta[Long].timap(TrackPointId.apply)(_.id)
  implicit val di: Meta[DeviceId] = Meta[Long].timap(DeviceId.apply)(_.id)
  implicit val si: Meta[SentenceKey] = Meta[Long].timap(SentenceKey.apply)(_.id)
  implicit val gpi: Meta[GPSPointId] = Meta[Long].timap(GPSPointId.apply)(_.id)
  implicit val gpf: Meta[GPSFix] = Meta[String].timap(GPSFix.orOther)(_.value)
  implicit val gsk: Meta[GPSSentenceKey] = Meta[Long].timap(GPSSentenceKey.apply)(_.id)
  implicit val pi: Meta[PushId] = Meta[Long].timap(PushId.apply)(_.id)
  implicit val pt: Meta[PushToken] = Meta[String].timap(PushToken.apply)(_.value)
  implicit val md: Meta[MobileDevice] = Meta[String].timap(MobileDevice.apply)(_.name)
  implicit val fi: Meta[FairwayId] = Meta[Long].timap(FairwayId.apply)(_.id)
  implicit val sa: Meta[SeaArea] = Meta[Int].timap(SeaArea.fromIntOrOther)(_.value)
  implicit val fl: Meta[FairwayLighting] =
    Meta[Int].timap(FairwayLighting.fromInt)(FairwayLighting.toInt)
  implicit val sk: Meta[SentenceKey] = Meta[Long].timap(SentenceKey.apply)(_.id)
  implicit val rs: Meta[RawSentence] = Meta[String].timap(RawSentence.apply)(_.sentence)
  implicit val ti: Meta[TrackId] = Meta[Long].timap(TrackId.apply)(_.id)
  implicit val tn: Meta[TrackName] = Meta[String].timap(TrackName.apply)(_.name)
  implicit val tt: Meta[TrackTitle] = Meta[String].timap(TrackTitle.apply)(_.title)
  implicit val tc: Meta[TrackCanonical] = Meta[String].timap(TrackCanonical.apply)(_.name)
  implicit val bn: Meta[BoatName] = Meta[String].timap(BoatName.apply)(_.name)
  implicit val bt: Meta[BoatToken] = Meta[String].timap(BoatToken.apply)(_.token)
  implicit val lon: Meta[Longitude] = Meta[Double].timap(Longitude.apply)(_.lng)
  implicit val lat: Meta[Latitude] = Meta[Double].timap(Latitude.apply)(_.lat)
  implicit val speed: Meta[SpeedM] = Meta[Double].timap(SpeedM.apply)(_.toMps)
  implicit val uid: Meta[UserId] = Meta[Long].timap(UserId.apply)(_.id)
  implicit val us: Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  implicit val em: Meta[Email] = Meta[String].timap(Email.apply)(_.value)
  implicit val ut: Meta[UserToken] = Meta[String].timap(UserToken.apply)(_.value)
  implicit val lan: Meta[Language] = Meta[String].timap(Language.apply)(_.value)
  implicit val ch: Meta[CoordHash] = Meta[String].timap(CoordHash.apply)(_.hash)
  implicit val temperature: Meta[Temperature] = Meta[Double].timap(Temperature.apply)(_.celsius)
  implicit val distanceMeta: Meta[DistanceM] = Meta[Double].timap(DistanceM.apply)(_.meters)
  implicit val du: Meta[FiniteDuration] =
    Meta[Double].timap(d => d.seconds)(_.toMillis.toDouble / 1000d)
  implicit val coordMeta: Meta[Coord] = Meta[Array[Byte]].timap(bytes =>
    toCoord(SpatialUtils.fromBytes[Point](bytes))
  )(SpatialUtils.coordToBytes)
  implicit val date: Meta[DateVal] = Meta[LocalDate].timap(d => DateVal(d))(_.toLocalDate)
  implicit val year: Meta[YearVal] = Meta[Int].timap(y => YearVal(y))(_.year)
  implicit val month: Meta[MonthVal] = Meta[Int].timap(m => MonthVal(m))(_.month)

  private def toCoord(point: Point): Coord = {
    val c = point.getCoordinate
    Coord(Longitude(c.x), Latitude(c.y))
  }
}
