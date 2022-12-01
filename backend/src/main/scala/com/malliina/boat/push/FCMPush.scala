package com.malliina.boat.push

import cats.{Functor, Monad}
import cats.effect.IO
import cats.syntax.all.toFunctorOps
import com.malliina.boat.push.FCMPush.log
import com.malliina.boat.{BoatName, FCMConf, PushToken}
import com.malliina.http.HttpClient
import com.malliina.push.fcm.FCMClientF
import com.malliina.push.gcm.{GCMMessage, GCMToken, GoogleClientF}
import com.malliina.util.AppLogger

object FCMPush:
  private val log = AppLogger(getClass)

  def build[F[+_]: Monad](conf: FCMConf, http: HttpClient[F]): FCMPush[F] =
    FCMPush(FCMClientF(conf.apiKey, http))

  def client[F[+_]: Monad](fcm: GoogleClientF[F]): FCMPush[F] = FCMPush(fcm)

class FCMPush[F[+_]: Functor](fcm: GoogleClientF[F]) extends PushClient[F, GCMToken]:
  override def push(notification: BoatNotification, to: GCMToken): F[PushSummary] =
    val message = GCMMessage(
      Map(
        BoatName.Key -> notification.boatName.name,
        BoatState.Key -> notification.state.name,
        BoatNotification.Message -> notification.message,
        BoatNotification.Title -> notification.title
      )
    )
    fcm.push(to, message).map { r =>
      val uninstalledCount = r.uninstalled.length
      val hasUninstalled = uninstalledCount > 0
      val suffix = if uninstalledCount > 1 then "s" else ""
      val detailed = if hasUninstalled then s" $uninstalledCount uninstalled device$suffix." else ""
      log.info(s"FCM push to '$to' complete.$detailed")
      PushSummary(
        Nil,
        r.uninstalled.map(t => PushToken(t.token)),
        r.replacements.map(PushTokenReplacement.apply)
      )
    }
