package com.malliina.boat.db

import com.malliina.boat.{Coord, CoordHash, Mmsi, MobileDevice, PushId, PushToken, UserToken, Utils, VesselName}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, JsonCompanion, RefreshToken, UserId, Username}

import java.time.Instant

case class PushDevice(
  id: PushId,
  token: PushToken,
  device: MobileDevice,
  user: UserId,
  added: Instant
)

case class PushInput(token: PushToken, device: MobileDevice, user: UserId)

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
