package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.db.PushInput
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.values.UserId

trait PushService {
  def enable(input: PushInput): IO[PushId]
  def disable(token: PushToken, user: UserId): IO[Boolean]
  def push(device: UserDevice, state: BoatState): IO[PushSummary]
}
