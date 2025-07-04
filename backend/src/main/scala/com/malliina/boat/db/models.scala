package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash, Mmsi, PushTokenType, PhoneId, PushId, PushToken, TrackName, UserToken, Utils, VesselName}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, JsonCompanion, RefreshToken, StringEnumCompanion, UserId, Username}

import java.time.Instant

case class PushDevice(
  id: PushId,
  token: PushToken,
  device: PushTokenType,
  phoneId: Option[PhoneId],
  liveActivityId: Option[TrackName],
  user: UserId,
  added: Instant
)

case class PushInput(
  token: PushToken,
  device: PushTokenType,
  deviceId: Option[PhoneId],
  liveActivityId: Option[TrackName],
  user: UserId
)

enum PushOutcome(val name: String):
  case Ended extends PushOutcome("ended")
  case Unknown extends PushOutcome("unknown")

object PushOutcome extends StringEnumCompanion[PushOutcome]:
  override def all: Seq[PushOutcome] = Seq(Ended, Unknown)
  override def write(t: PushOutcome): String = t.name

case class DbTrackInfo(
  avgWaterTemp: Option[Temperature],
  avgOutsideTemp: Option[Temperature],
  distance: DistanceM,
  points: Int
)

case class PreviousPoint(coord: Coord, trackIndex: Int)

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])

case class NewUser(user: Username, email: Option[Email], token: UserToken, enabled: Boolean)

object NewUser:
  def email(email: Email): NewUser =
    NewUser(Username(email.email), Option(email), UserToken.random(), enabled = true)

opaque type RefreshTokenId = String
object RefreshTokenId extends JsonCompanion[String, RefreshTokenId]:
  override def apply(raw: String): RefreshTokenId = raw
  override def write(t: RefreshTokenId): String = t

  def random(): RefreshTokenId = RefreshTokenId(Utils.randomString(32))

enum RefreshService(val name: String):
  case SIWA extends RefreshService("siwa")
  case Polestar extends RefreshService("polestar")
  override def toString = name

object RefreshService extends StringEnumCompanion[RefreshService]:
  override def all: Seq[RefreshService] = Seq(SIWA, Polestar)
  override def write(t: RefreshService): String = t.name

case class RefreshRow(
  id: RefreshTokenId,
  token: RefreshToken,
  owner: UserId,
  lastVerification: Instant,
  canVerify: Boolean,
  added: Instant
)

case class MmsiRow(mmsi: Mmsi, name: VesselName, draft: DistanceM)
case class MmsiUpdateRow(
  mmsi: Mmsi,
  coord: Coord,
  sog: SpeedM,
  cog: Double,
  destination: Option[String],
  heading: Option[Int],
  eta: Long,
  timestampMillis: Long
)
