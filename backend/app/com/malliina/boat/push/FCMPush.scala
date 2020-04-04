package com.malliina.boat.push

import com.malliina.boat.push.FCMPush.log
import com.malliina.boat.{BoatName, PushToken}
import com.malliina.push.fcm.FCMLegacyClient
import com.malliina.push.gcm.{GCMMessage, GCMToken}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

object FCMPush {
  private val log = Logger(getClass)

  def apply(conf: Configuration, ec: ExecutionContext): FCMPush =
    apply(FCMLegacyClient(conf.get[String]("boat.push.fcm.apiKey")), ec)

  def apply(fcm: FCMLegacyClient, ec: ExecutionContext): FCMPush =
    new FCMPush(fcm)(ec)
}

class FCMPush(fcm: FCMLegacyClient)(implicit ec: ExecutionContext) extends PushClient[GCMToken] {
  override def push(notification: BoatNotification, to: GCMToken): Future[PushSummary] = {
    val message = GCMMessage(
      Map(
        BoatName.Key -> notification.boatName.name,
        BoatState.Key -> notification.state.name,
        BoatNotification.Message -> notification.message,
        BoatNotification.Title -> notification.title
      )
    )
    fcm.push(to, message).map { r =>
      log.info(s"Push to '$to' complete. ${r.uninstalled.length} uninstalled device(s).")
      PushSummary(
        r.uninstalled.map(t => PushToken(t.token)),
        r.replacements.map(PushTokenReplacement.apply)
      )
    }
  }
}
