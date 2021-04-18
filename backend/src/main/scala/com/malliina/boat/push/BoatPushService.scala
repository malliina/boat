package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.MobileDevice.{Android, IOS, Unknown}
import com.malliina.boat.PushConf
import com.malliina.boat.db.PushDevice
import com.malliina.boat.push.BoatPushService.log
import com.malliina.http.HttpClient
import com.malliina.push.apns.APNSToken
import com.malliina.push.gcm.GCMToken
import com.malliina.util.AppLogger

object BoatPushService {
  private val log = AppLogger(getClass)

  def apply(ios: APNS, android: PushClient[GCMToken]): BoatPushService =
    new BoatPushService(ios, android)

  def apply(c: PushConf, http: HttpClient[IO]): BoatPushService =
    apply(APNSPush(c.apns, http), FCMPush(c.fcm, http))
}

class BoatPushService(ios: APNS, android: PushClient[GCMToken]) extends PushEndpoint {
  override def push(notification: BoatNotification, to: PushDevice): IO[PushSummary] =
    to.device match {
      case IOS =>
        ios.push(notification, APNSToken(to.token.token))
      case Android =>
        android.push(notification, GCMToken(to.token.token))
      case Unknown(name) =>
        log.error(s"Unsupported device: '$name'. Ignoring push request.")
        IO.pure(PushSummary.empty)
    }
}
