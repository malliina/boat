package com.malliina.boat.db

import java.time.LocalDate

import com.malliina.boat.{BoatName, BoatToken, Coord, DateVal, Latitude, Longitude, MonthVal, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, YearVal}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.Username
import com.vividsolutions.jts.geom.Point
import doobie._

import scala.concurrent.duration.FiniteDuration
import concurrent.duration.DurationDouble

object doobieMappings {
  implicit val instantMeta = doobie.implicits.legacy.instant.JavaTimeInstantMeta
  implicit val localDateMeta = doobie.implicits.legacy.localdate.JavaTimeLocalDateMeta

  implicit val tpi: Meta[TrackPointId] = Meta[Long].timap(TrackPointId.apply)(_.id)
  implicit val ti: Meta[TrackId] = Meta[Long].timap(TrackId.apply)(_.id)
  implicit val tn: Meta[TrackName] = Meta[String].timap(TrackName.apply)(_.name)
  implicit val tt: Meta[TrackTitle] = Meta[String].timap(TrackTitle.apply)(_.title)
  implicit val tc: Meta[TrackCanonical] = Meta[String].timap(TrackCanonical.apply)(_.name)
  implicit val bn: Meta[BoatName] = Meta[String].timap(BoatName.apply)(_.name)
  implicit val bt: Meta[BoatToken] = Meta[String].timap(BoatToken.apply)(_.token)
  implicit val lon: Meta[Longitude] = Meta[Double].timap(Longitude.apply)(_.lng)
  implicit val lat: Meta[Latitude] = Meta[Double].timap(Latitude.apply)(_.lat)
  implicit val speed: Meta[SpeedM] = Meta[Double].timap(SpeedM.apply)(_.toMps)
  implicit val us: Meta[Username] = Meta[String].timap(Username.apply)(_.name)
  implicit val temperature: Meta[Temperature] = Meta[Double].timap(Temperature.apply)(_.celsius)
  implicit val distance: Meta[DistanceM] = Meta[Double].timap(DistanceM.apply)(_.meters)
  implicit val du: Meta[FiniteDuration] =
    Meta[Double].timap(d => d.seconds)(_.toMillis.toDouble / 1000d)
  implicit val coord: Meta[Coord] = Meta[Array[Byte]].timap(bytes =>
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
