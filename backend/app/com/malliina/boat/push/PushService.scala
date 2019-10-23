package com.malliina.boat.push

import com.malliina.boat.{PushId, PushToken, UserDevice}
import com.malliina.boat.db.PushInput
import com.malliina.values.UserId

import scala.concurrent.Future

trait PushService {
  def enable(input: PushInput): Future[PushId]
  def disable(token: PushToken, user: UserId): Future[Boolean]
  def push(device: UserDevice, state: BoatState): Future[Unit]
}
