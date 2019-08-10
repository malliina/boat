package com.malliina.boat.db

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.{Instant, LocalDate, LocalTime}
import java.util.concurrent.TimeUnit

import com.malliina.boat._
import com.malliina.boat.db.SpatialUtils.{coordToBytes, fromBytes}
import com.malliina.measure.{DistanceDoubleM, DistanceM, SpeedDoubleM, SpeedM, Temperature, TemperatureDouble}
import com.malliina.values._
import com.vividsolutions.jts.geom.Point
import slick.ast.{BaseTypedType, FieldSymbol}
import slick.jdbc.{JdbcProfile, JdbcType}

import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.reflect.ClassTag

trait Mappings { self: JdbcComponent =>
  import api._

  implicit val dayMapping = MappedColumnType.base[DayVal, Int](_.day, DayVal.apply)
  implicit val monthMapping = MappedColumnType.base[MonthVal, Int](_.month, MonthVal.apply)
  implicit val yearMapping = MappedColumnType.base[YearVal, Int](_.year, YearVal.apply)
  implicit val durationMapping =
    MappedColumnType.base[FiniteDuration, Double](_.toUnit(TimeUnit.SECONDS), _.seconds)
  implicit val password = stringMapping(Password.apply)
  implicit val sentenceIdMapping = longMapping(SentenceKey.apply)
  implicit val gpsSentenceIdMapping = longMapping(GPSSentenceKey.apply)
  implicit val gpsPointIdMapping = longMapping(GPSPointId.apply)
  implicit val sentenceMapping = stringMapping(RawSentence.apply)
  implicit val userIdMapping = longMapping(UserId.apply)
  implicit val trackIdMapping = longMapping(TrackId.apply)
  implicit val pointIdMapping = longMapping(TrackPointId.apply)
  implicit val boatIdMapping = longMapping(BoatId.apply)
  implicit val fairwayIdMapping = longMapping(FairwayId.apply)
  implicit val fairwayCoordIdMapping = longMapping(FairwayCoordId.apply)
  implicit val boatNameMapping = stringMapping(BoatName.apply)
  implicit val boatTokenMapping = stringMapping(BoatToken.apply)
  implicit val trackNameMapping = stringMapping(TrackName.apply)
  implicit val userMapping = stringMapping(Username.apply)
  implicit val userTokenMapping = stringMapping(UserToken.apply)
  implicit val emailMapping = stringMapping(Email.apply)
  implicit val coordHashMapping =
    MappedColumnType.base[CoordHash, String](_.hash, h => CoordHash(h))
  implicit val distanceMapping =
    MappedColumnType.base[DistanceM, Double](_.toMeters, (m: Double) => m.meters)
  implicit val speedMapping = MappedColumnType.base[SpeedM, Double](_.toKmh, _.kmh)
  implicit val temperatureMapping =
    MappedColumnType.base[Temperature, Double](_.toCelsius, _.celsius)
  implicit val instantMapping =
    MappedColumnType
      .base[Instant, java.sql.Timestamp](i => java.sql.Timestamp.from(i), t => t.toInstant)
  implicit val timeMapping =
    MappedColumnType.base[LocalTime, java.sql.Time](java.sql.Time.valueOf, _.toLocalTime)
  implicit val dateMapping =
    MappedColumnType.base[LocalDate, java.sql.Date](java.sql.Date.valueOf, _.toLocalDate)
  implicit val dateValMapping =
    MappedColumnType.base[DateVal, java.sql.Date](dv => java.sql.Date.valueOf(dv.toLocalDate), d => DateVal(d.toLocalDate))
  implicit val deviceMapping =
    MappedColumnType.base[MobileDevice, String](_.name, MobileDevice.apply)
  implicit val pushMapping = stringMapping(PushToken.apply)
  implicit val pushIdMapping = longMapping(PushId.apply)
  implicit val trackTitleMapping = stringMapping(TrackTitle.apply)
  implicit val canonicalMapping = stringMapping(TrackCanonical.apply)
  implicit val languageMapping = stringMapping(Language.apply)
  implicit val longitudeMapping =
    MappedColumnType.base[Longitude, Double](_.lng, (lng: Double) => Longitude(lng))
  implicit val latitudeMapping =
    MappedColumnType.base[Latitude, Double](_.lat, (lat: Double) => Latitude(lat))
  implicit val fairwayLightingMapping =
    MappedColumnType.base[FairwayLighting, Int](FairwayLighting.toInt, FairwayLighting.fromInt)
  implicit val fairwayTypeMapping =
    MappedColumnType.base[FairwaySeaType, Int](FairwaySeaType.toInt, FairwaySeaType.fromInt)
  implicit val seaTypeMapping =
    MappedColumnType.base[SeaArea, Int](_.value, SeaArea.fromIntOrOther)

  class InstantJdbcType(implicit override val classTag: ClassTag[Instant])
    extends jdbc.DriverJdbcType[Instant] {

    override def sqlType: Int = java.sql.Types.TIMESTAMP
    override def sqlTypeName(sym: Option[FieldSymbol]) =
      "TIMESTAMP(3)"
    override def setValue(v: Instant, p: PreparedStatement, idx: Int): Unit =
      p.setTimestamp(idx, Timestamp.from(v))
    override def getValue(r: ResultSet, idx: Int): Instant =
      Option(r.getTimestamp(idx)).map(_.toInstant).orNull
    override def updateValue(v: Instant, r: ResultSet, idx: Int): Unit =
      r.updateTimestamp(idx, Timestamp.from(v))
    override def valueToSQLLiteral(value: Instant): String =
      s"'${Timestamp.from(value)}'"
  }

  class CoordJdbcType(implicit override val classTag: ClassTag[Coord])
    extends jdbc.DriverJdbcType[Coord] {

    override def sqlType: Int = java.sql.Types.OTHER
    override def sqlTypeName(sym: Option[FieldSymbol]): String = "geometry"
    override def getValue(r: ResultSet, idx: Int): Coord = {
      val value = r.getBytes(idx)
      if (r.wasNull) null.asInstanceOf[Coord] else toCoord(fromBytes[Point](value))
    }
    def toCoord(point: Point): Coord = {
      val c = point.getCoordinate
      Coord(Longitude(c.x), Latitude(c.y))
    }
    override def setValue(coord: Coord, p: PreparedStatement, idx: Int): Unit =
      p.setBytes(idx, coordToBytes(coord))
    override def updateValue(v: Coord, r: ResultSet, idx: Int): Unit =
      r.updateBytes(idx, coordToBytes(v))
    override def hasLiteralForm: Boolean = false
    override def valueToSQLLiteral(v: Coord): String = throw new SlickException("no literal form")
  }

  //  implicit val boatInstantMapper: InstantJdbcType = new InstantJdbcType
  implicit val coordMapper: CoordJdbcType = new CoordJdbcType

  def stringMapping[T <: WrappedString: ClassTag](
                                                   apply: String => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, String](_.value, apply)

  def longMapping[T <: WrappedId: ClassTag](apply: Long => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, Long](_.id, apply)
}
