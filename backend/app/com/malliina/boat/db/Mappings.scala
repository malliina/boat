package com.malliina.boat.db

import java.sql.{PreparedStatement, ResultSet}
import java.time.{Instant, LocalDate, LocalTime}

import com.malliina.boat.db.SpatialUtils.{coordToBytes, fromBytes}
import com.malliina.boat._
import com.malliina.measure.{Distance, DistanceDouble, Speed, SpeedDouble, Temperature, TemperatureDouble}
import com.malliina.values._
import com.vividsolutions.jts.geom.Point
import slick.ast.{BaseTypedType, FieldSymbol}
import slick.jdbc.{JdbcProfile, JdbcType}

import scala.reflect.ClassTag

class Mappings(val impl: JdbcProfile) {

  import impl.api._

  implicit val password = stringMapping(Password.apply)
  implicit val sentenceIdMapping = longMapping(SentenceKey.apply)
  implicit val sentenceMapping = stringMapping(RawSentence.apply)
  implicit val userIdMapping = longMapping(UserId.apply)
  implicit val trackIdMapping = longMapping(TrackId.apply)
  implicit val pointIdMapping = longMapping(TrackPointId.apply)
  implicit val boatIdMapping = longMapping(BoatId.apply)
  implicit val boatNameMapping = stringMapping(BoatName.apply)
  implicit val boatTokenMapping = stringMapping(BoatToken.apply)
  implicit val trackNameMapping = stringMapping(TrackName.apply)
  implicit val userMapping = stringMapping(Username.apply)
  implicit val userTokenMapping = stringMapping(UserToken.apply)
  implicit val emailMapping = stringMapping(Email.apply)
  implicit val distanceMappingMeters = MappedColumnType.base[Distance, Double](_.toMetersDouble, (m: Double) => m.meters)
  implicit val speedMapping = MappedColumnType.base[Speed, Double](_.toKmh, _.kmh)
  implicit val temperatureMapping = MappedColumnType.base[Temperature, Double](_.toCelsius, _.celsius)
  implicit val instantMapping = MappedColumnType.base[Instant, java.sql.Timestamp](java.sql.Timestamp.from, _.toInstant)
  implicit val timeMapping = MappedColumnType.base[LocalTime, java.sql.Time](java.sql.Time.valueOf, _.toLocalTime)
  implicit val dateMapping = MappedColumnType.base[LocalDate, java.sql.Date](java.sql.Date.valueOf, _.toLocalDate)
  implicit val deviceMapping = MappedColumnType.base[MobileDevice, String](_.name, MobileDevice.apply)
  implicit val pushMapping = stringMapping(PushToken.apply)
  implicit val pushIdMapping = longMapping(PushId.apply)
  implicit val trackTitleMapping = stringMapping(TrackTitle.apply)
  implicit val canonicalMapping = stringMapping(TrackCanonical.apply)
  implicit val languageMapping = stringMapping(Language.apply)

  class CoordJdbcType(implicit override val classTag: ClassTag[Coord])
    extends impl.DriverJdbcType[Coord] {

    override def sqlType: Int = java.sql.Types.OTHER

    override def sqlTypeName(sym: Option[FieldSymbol]): String = "geometry"

    override def getValue(r: ResultSet, idx: Int): Coord = {
      val value = r.getBytes(idx)
      if (r.wasNull) null.asInstanceOf[Coord] else toCoord(fromBytes[Point](value))
    }

    def toCoord(point: Point): Coord = {
      val c = point.getCoordinate
      Coord(c.x, c.y)
    }

    override def setValue(coord: Coord, p: PreparedStatement, idx: Int): Unit =
      p.setBytes(idx, coordToBytes(coord))

    override def updateValue(v: Coord, r: ResultSet, idx: Int): Unit =
      r.updateBytes(idx, coordToBytes(v))

    override def hasLiteralForm: Boolean = false

    override def valueToSQLLiteral(v: Coord): String = throw new SlickException("no literal form")
  }

  implicit val coordMapper = new CoordJdbcType

  def stringMapping[T <: Wrapped : ClassTag](apply: String => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, String](_.value, apply)

  def longMapping[T <: WrappedId : ClassTag](apply: Long => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, Long](_.id, apply)
}
