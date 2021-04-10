package com.malliina.boat.push

import cats.effect.{ContextShift, IO}
import com.malliina.boat.push.FCMPush.log
import com.malliina.boat.{BoatName, FCMConf, PushToken}
import com.malliina.push.fcm.FCMLegacyClient
import com.malliina.push.gcm.{GCMMessage, GCMToken}
import com.malliina.util.AppLogger

object FCMPush {
  private val log = AppLogger(getClass)

  def apply(conf: FCMConf, cs: ContextShift[IO]): FCMPush =
    apply(FCMLegacyClient(conf.apiKey), cs)

  def apply(fcm: FCMLegacyClient, cs: ContextShift[IO]): FCMPush =
    new FCMPush(fcm)(cs)
}

class FCMPush(fcm: FCMLegacyClient)(implicit cs: ContextShift[IO]) extends PushClient[GCMToken] {
  override def push(notification: BoatNotification, to: GCMToken): IO[PushSummary] = {
    val message = GCMMessage(
      Map(
        BoatName.Key -> notification.boatName.name,
        BoatState.Key -> notification.state.name,
        BoatNotification.Message -> notification.message,
        BoatNotification.Title -> notification.title
      )
    )
    IO.fromFuture(IO(fcm.push(to, message))).map { r =>
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
