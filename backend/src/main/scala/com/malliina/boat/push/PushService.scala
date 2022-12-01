package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.db.PushInput
import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.values.UserId

trait PushService[F[_]]:
  def enable(input: PushInput): F[PushId]
  def disable(token: PushToken, user: UserId): F[Boolean]
  def push(device: UserDevice, state: BoatState): F[PushSummary]
