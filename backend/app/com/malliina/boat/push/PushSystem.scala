package com.malliina.boat.push

import com.malliina.push.apns.APNSToken

import scala.concurrent.Future

trait PushSystem {
  def push(notification: BoatNotification, to: APNSToken): Future[Seq[APNSHttpResult]]
}
