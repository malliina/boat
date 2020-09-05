package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{MobileDevice, PushId, PushToken}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.UserId

case class PushDevice(
  id: PushId,
  token: PushToken,
  device: MobileDevice,
  user: UserId,
  added: Instant
)

case class PushInput(token: PushToken, device: MobileDevice, user: UserId)

case class TrackInfo(avgTemp: Temperature, distance: DistanceM, points: Int)
