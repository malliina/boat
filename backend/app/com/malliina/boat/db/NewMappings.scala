package com.malliina.boat.db

import java.time.{Instant, ZoneOffset}
import java.util.Date

import com.malliina.boat.{Coord, DateVal, Latitude, Longitude}
import com.malliina.measure.{SpeedDoubleM, SpeedM}
import com.malliina.values.UserId
import com.vividsolutions.jts.geom.Point
import io.getquill.MappedEncoding

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object NewMappings extends NewMappings

trait NewMappings {
  implicit val instantDecoder = MappedEncoding[Date, Instant](d => d.toInstant)
  implicit val instantEncoder = MappedEncoding[Instant, Date](i => Date.from(i))

  implicit val speedDecoder = MappedEncoding[Double, SpeedM](_.kmh)
  implicit val speedEncoder = MappedEncoding[SpeedM, Double](_.toKmh)

  implicit val dateValDecoder =
    MappedEncoding[Instant, DateVal](
      d => DateVal(d.atOffset(ZoneOffset.UTC).toLocalDate)
    )
  implicit val userIdDecoder = MappedEncoding[Long, UserId](UserId.apply)
  implicit val userIdEncoder = MappedEncoding[UserId, Long](_.id)

  implicit val coordDecoder = MappedEncoding[Array[Byte], Coord] { bytes =>
    toCoord(SpatialUtils.fromBytes[Point](bytes))
  }
  implicit val coordEncoder = MappedEncoding[Coord, Array[Byte]] { coord =>
    SpatialUtils.coordToBytes(coord)
  }
  implicit val durationDecoder =
    MappedEncoding[Double, FiniteDuration](_.seconds)

  private def toCoord(point: Point): Coord = {
    val c = point.getCoordinate
    Coord(Longitude(c.x), Latitude(c.y))
  }
}
