package com.malliina.boat.push

import cats.{Applicative, Monad}
import com.malliina.boat.MobileDevice.{Android, IOS, Unknown}
import com.malliina.boat.PushConf
import com.malliina.boat.db.PushDevice
import com.malliina.boat.push.BoatPushService.log
import com.malliina.http.HttpClient
import com.malliina.push.apns.APNSToken
import com.malliina.push.gcm.GCMToken
import com.malliina.util.AppLogger

object BoatPushService:
  private val log = AppLogger(getClass)

  def fromConf[F[_]: Monad](c: PushConf, http: HttpClient[F]): BoatPushService[F] =
    BoatPushService(APNSPush.fromConf(c.apns, http), FCMPush.build(c.fcm, http))

class BoatPushService[F[_]: Applicative](ios: APNS[F], android: PushClient[F, GCMToken])
  extends PushEndpoint[F]:
  val F = Applicative[F]
  override def push(notification: SourceNotification, to: PushDevice): F[PushSummary] =
    to.device match
      case IOS =>
        ios.push(notification, APNSToken(to.token.token))
      case Android =>
        android.push(notification, GCMToken(to.token.token))
      case Unknown(name) =>
        log.error(s"Unsupported device: '$name'. Ignoring push request.")
        F.pure(PushSummary.empty)
