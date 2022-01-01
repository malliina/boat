package com.malliina.boat.db

import com.malliina.boat.{CoordHash, MobileDevice, PushId, PushToken, UserToken}
import com.malliina.boat.Utils
import com.malliina.measure.{DistanceM, Temperature}
import com.malliina.values.{Email, IdCompanion, RefreshToken, StringCompanion, UserId, Username, WrappedId, WrappedString}
import com.sun.jdi.PrimitiveValue

import java.time.Instant
import java.time.temporal.ChronoUnit

case class PushDevice(
  id: PushId,
  token: PushToken,
  device: MobileDevice,
  user: UserId,
  added: Instant
)

case class PushInput(token: PushToken, device: MobileDevice, user: UserId)

case class DbTrackInfo(avgTemp: Temperature, distance: DistanceM, points: Int)

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])

case class NewUser(user: Username, email: Option[Email], token: UserToken, enabled: Boolean)

object NewUser:
  def email(email: Email): NewUser =
    NewUser(Username(email.email), Option(email), UserToken.random(), enabled = true)

class NotFoundException(val message: String) extends Exception

case class RefreshTokenId(value: String) extends AnyVal with WrappedString
object RefreshTokenId extends StringCompanion[RefreshTokenId]:
  def random(): RefreshTokenId = RefreshTokenId(Utils.randomString(32))

case class RefreshRow(
  id: RefreshTokenId,
  token: RefreshToken,
  owner: UserId,
  lastVerification: Instant,
  canVerify: Boolean,
  added: Instant
)
