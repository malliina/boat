package com.malliina.boat.db

import cats.Show
import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.db.Values.VesselUpdateId

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import com.malliina.boat.parsing.GPSFix
import com.malliina.boat.{AisUpdateId, BoatName, BoatToken, CarUpdateId, Coord, CoordHash, DateVal, DeviceId, Energy, FairwayLighting, InviteState, Language, Latitude, LiveActivityId, Longitude, Mmsi, PushTokenType, MonthVal, PhoneId, PushId, PushToken, RawSentence, SeaArea, SentenceKey, SourceType, TrackCanonical, TrackId, TrackName, TrackPointId, TrackTitle, UserAgent, UserToken, VesselName, VesselRowId, YearVal}
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

  given Meta[TrackPointId] = validated(TrackPointId)
  given Meta[RefreshTokenId] = validated(RefreshTokenId)
  given Meta[RefreshService] = validated(RefreshService)
  given Meta[DeviceId] = validated(DeviceId)
  given Meta[GPSFix] = Meta[String].timap(GPSFix.orOther)(_.value)
  given Meta[PushId] = validated(PushId)
  given Meta[PushToken] = validated(PushToken)
  given Meta[LiveActivityId] = validated(LiveActivityId)
  given Meta[PhoneId] = validated(PhoneId)
  given Meta[PushTokenType] = Meta[String].timap(PushTokenType.orUnknown)(_.name)
  given Meta[PushOutcome] = validated(PushOutcome)
  given Meta[SeaArea] = Meta[Int].timap(SeaArea.fromIntOrOther)(_.value)
  given Meta[FairwayLighting] =
    Meta[Int].timap(FairwayLighting.fromInt)(FairwayLighting.toInt)
  given Meta[RawSentence] = validated(RawSentence)
  given Meta[TrackId] = validated(TrackId)
  given Meta[VesselRowId] = validated(VesselRowId)
  given Meta[AisUpdateId] = validated(AisUpdateId)
  given Meta[VesselUpdateId] = Meta[Long].timap(VesselUpdateId.apply)(_.raw)
  given Meta[Mmsi] = validated(Mmsi)
  given Meta[VesselName] = validated(VesselName)
  given Meta[FairwayId] = validated(FairwayId)
  given Meta[FairwayCoordId] = validated(FairwayCoordId)
  given Meta[TrackName] = validated(TrackName)
  given Meta[TrackTitle] = validated(TrackTitle)
  given Meta[TrackCanonical] = validated(TrackCanonical)
  given Meta[BoatName] = validated(BoatName)
  given Meta[BoatToken] = validated(BoatToken)
  given Meta[UserToken] = validated(UserToken)
  given Meta[Longitude] = validated(Longitude)
  given Meta[Latitude] = validated(Latitude)
  given Meta[SpeedM] = Meta[Double].timap(_.kmh)(_.toKmh)
  given Meta[UserId] = validated(UserId)
  given Meta[Username] = validated(Username)
  given Meta[Email] = validated(Email)
  given Meta[Language] = validated(Language)
  given Meta[CoordHash] = Meta[String].timap(CoordHash.fromString)(_.hash)
  given Meta[Temperature] = Meta[Double].timap(Temperature.apply)(_.celsius)
  given Meta[DistanceM] = Meta[Double].timap(DistanceM.apply)(_.meters)
  given Meta[FiniteDuration] =
    Meta[Double].timap(d => d.seconds)(_.toMillis.toDouble / 1000d)
  given Show[Array[Byte]] = bs => s"${bs.size} bytes"
  given Meta[Coord] = Meta[Array[Byte]].tiemap(bytes =>
    toCoord(SpatialUtils.fromBytes[Point](bytes)).left.map(_.message)
  )(SpatialUtils.coordToBytes)
  given Meta[DateVal] = Meta[LocalDate].timap(d => DateVal(d))(_.toLocalDate)
  given Meta[YearVal] = validated(YearVal)
  given Meta[MonthVal] = validated(MonthVal)
  given Meta[InviteState] =
    Meta[String].timap(s => InviteState.orOther(s))(_.name)
  given Meta[SourceType] = Meta[String].timap(s => SourceType.orOther(s))(_.name)
  given Meta[SentenceKey] = validated(SentenceKey)
  given Meta[Degrees] = validated(Degrees)
  given Meta[CarUpdateId] = validated(CarUpdateId)
  given Meta[Energy] = validated(Energy)
  given Meta[Host] =
    Meta[String].tiemap(s => Host.fromString(s).toRight(s"Invalid host: '$s'."))(_.show)
  given Meta[Port] =
    Meta[Int].tiemap(i => Port.fromInt(i).toRight(s"Invalid port: '$i'."))(_.value)
  given Meta[UserAgent] = validated(UserAgent)

  private def validated[T, R: {Meta, Show}, C <: ValidatingCompanion[R, T]](c: C): Meta[T] =
    Meta[R].tiemap(r => c.build(r).left.map(err => err.message))(c.write)

  private def toCoord(point: Point) =
    val c = point.getCoordinate
    for
      lon <- Longitude.build(c.x)
      lat <- Latitude.build(c.y)
    yield Coord(lon, lat)
