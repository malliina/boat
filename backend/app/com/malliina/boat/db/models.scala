package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{CoordHash, MobileDevice, PushId, PushToken, UserToken}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}

case class PushDevice(
  id: PushId,
  token: PushToken,
  device: MobileDevice,
  user: UserId,
  added: Instant
)

case class PushInput(token: PushToken, device: MobileDevice, user: UserId)

case class TrackInfo(avgTemp: Temperature, distance: DistanceM, points: Int)

case class CoordFairway(coord: CoordHash, fairway: FairwayRow)
case class CoordFairways(coord: CoordHash, fairways: Seq[FairwayRow])

case class NewUser(user: Username, email: Option[Email], token: UserToken, enabled: Boolean)

object NewUser {
  def email(email: Email): NewUser =
    NewUser(Username(email.email), Option(email), UserToken.random(), enabled = true)
}

class NotFoundException(val message: String) extends Exception
