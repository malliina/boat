package com.malliina.boat.db

import java.time.{Instant, LocalDate, LocalTime}

import com.malliina.boat.{BoatId, BoatName, BoatToken, RawSentence, SentenceKey, TrackId, TrackName, TrackPointId, User, UserEmail, UserId, WrappedId}
import com.malliina.measure.{Distance, DistanceLong, Speed, SpeedDouble, Temperature, TemperatureDouble}
import com.malliina.play.models.{Password, Username}
import com.malliina.values.Wrapped
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

import scala.reflect.ClassTag

class Mappings(val impl: JdbcProfile) {

  import impl.api._

  implicit val username = MappedColumnType.base[Username, String](Username.raw, Username.apply)
  implicit val password = MappedColumnType.base[Password, String](Password.raw, Password.apply)
  implicit val sentenceIdMapping = longMapping(SentenceKey.apply)
  implicit val sentenceMapping = stringMapping(RawSentence.apply)
  implicit val userIdMapping = longMapping(UserId.apply)
  implicit val trackIdMapping = longMapping(TrackId.apply)
  implicit val pointIdMapping = longMapping(TrackPointId.apply)
  implicit val boatIdMapping = longMapping(BoatId.apply)
  implicit val boatNameMapping = stringMapping(BoatName.apply)
  implicit val boatTokenMapping = stringMapping(BoatToken.apply)
  implicit val trackNameMapping = stringMapping(TrackName.apply)
  implicit val userMapping = stringMapping(User.apply)
  implicit val emailMapping = stringMapping(UserEmail.apply)
  implicit val distanceMapping = MappedColumnType.base[Distance, Long](_.toMillis, _.millimeters)
  implicit val speedMapping = MappedColumnType.base[Speed, Double](_.toKmh, _.kmh)
  implicit val temperatureMapping = MappedColumnType.base[Temperature, Double](_.toCelsius, _.celsius)
  implicit val instantMapping = MappedColumnType.base[Instant, java.sql.Timestamp](java.sql.Timestamp.from, _.toInstant)
  implicit val timeMapping = MappedColumnType.base[LocalTime, java.sql.Time](java.sql.Time.valueOf, _.toLocalTime)
  implicit val dateMapping = MappedColumnType.base[LocalDate, java.sql.Date](java.sql.Date.valueOf, _.toLocalDate)

  def stringMapping[T <: Wrapped : ClassTag](apply: String => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, String](_.value, apply)

  def longMapping[T <: WrappedId : ClassTag](apply: Long => T): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, Long](_.id, apply)
}
