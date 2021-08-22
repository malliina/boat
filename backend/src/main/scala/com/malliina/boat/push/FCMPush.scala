package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.push.FCMPush.log
import com.malliina.boat.{BoatName, FCMConf, PushToken}
import com.malliina.http.HttpClient
import com.malliina.push.fcm.FCMClientF
import com.malliina.push.gcm.{GCMMessage, GCMToken, GoogleClientF}
import com.malliina.util.AppLogger

object FCMPush {
  private val log = AppLogger(getClass)

  def apply(conf: FCMConf, http: HttpClient[IO]): FCMPush = apply(FCMClientF(conf.apiKey, http))

  def apply(fcm: GoogleClientF[IO]): FCMPush = new FCMPush(fcm)
}

class FCMPush(fcm: GoogleClientF[IO]) extends PushClient[GCMToken] {
  override def push(notification: BoatNotification, to: GCMToken): IO[PushSummary] = {
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
      val suffix = if (uninstalledCount > 1) "s" else ""
      val detailed = if (hasUninstalled) s" $uninstalledCount uninstalled device$suffix." else ""
      log.info(s"FCM push to '$to' complete.$detailed")
      PushSummary(
        r.uninstalled.map(t => PushToken(t.token)),
        r.replacements.map(PushTokenReplacement.apply)
      )
    }
  }
}
