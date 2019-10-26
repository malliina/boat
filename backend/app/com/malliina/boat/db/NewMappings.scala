package com.malliina.boat.db

import java.time.{Instant, LocalDate}
import java.util.Date

import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{Coord, DateVal, FairwayLighting, Latitude, Longitude, MobileDevice, SeaArea}
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

  implicit val mobileDeviceDecoder = MappedEncoding[String, MobileDevice](MobileDevice.apply)
  implicit val mobileDeviceEncoder = MappedEncoding[MobileDevice, String](_.name)

  implicit val dateValDecoder = MappedEncoding[LocalDate, DateVal](d => DateVal(d))
  implicit val dateValEnc = MappedEncoding[DateVal, LocalDate](d => d.toLocalDate)

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

  implicit val lightingDecoder = MappedEncoding[Int, FairwayLighting](FairwayLighting.fromInt)
  implicit val lightingEncoder = MappedEncoding[FairwayLighting, Int](FairwayLighting.toInt)
  implicit val seaAreaDecoder = MappedEncoding[Int, SeaArea](SeaArea.fromIntOrOther)
  implicit val seaAreaEncoder = MappedEncoding[SeaArea, Int](_.value)

  implicit val gpsFixDecoder = MappedEncoding[String, GPSFix](GPSFix.orOther)
  implicit val gpsFixEncoder = MappedEncoding[GPSFix, String](_.value)

  private def toCoord(point: Point): Coord = {
    val c = point.getCoordinate
    Coord(Longitude(c.x), Latitude(c.y))
  }
}
