package com.malliina.boat.push

import com.malliina.boat.{BoatName, PushToken}
import com.malliina.push.fcm.FCMLegacyClient
import com.malliina.push.gcm.{GCMMessage, GCMToken}
import play.api.{Configuration, Logger}
import FCMPush.log
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
    val message = GCMMessage(Map(
      BoatName.Key -> notification.boatName.name,
      BoatState.Key -> notification.state.name,
      "message" -> notification.message
    ))
    fcm.push(to, message).map { r =>
      log.info(s"Push to '$to' complete. ${r.uninstalled.length}")
      PushSummary(
        r.uninstalled.map(t => PushToken(t.token)),
        r.replacements.map(PushTokenReplacement.apply)
      )
    }
  }
}
