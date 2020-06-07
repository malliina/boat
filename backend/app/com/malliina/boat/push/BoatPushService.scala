package com.malliina.boat.push

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import com.malliina.boat.MobileDevice.{Android, IOS, Unknown}
import com.malliina.boat.db.PushDevice
import com.malliina.boat.push.BoatPushService.log
import com.malliina.push.apns.APNSToken
import com.malliina.push.gcm.GCMToken
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

object BoatPushService {
  private val log = Logger(getClass)

  def apply(conf: Configuration, ec: ExecutionContext): BoatPushService =
    apply(APNSPush(conf), FCMPush(conf, ec))

  def apply(ios: APNS, android: PushClient[GCMToken]): BoatPushService =
    new BoatPushService(ios, android)
}

class BoatPushService(ios: APNS, android: PushClient[GCMToken]) extends PushEndpoint {
  testFiles()

  def testFiles(): Unit = {
    val path = "/files"
    val fs = Files.list(Paths.get(path)).iterator().asScala.toList
    log.info(s"${fs.length} files in $path: ${fs.mkString(", ")}")
  }

  override def push(notification: BoatNotification, to: PushDevice): Future[PushSummary] =
    to.device match {
      case IOS =>
        ios.push(notification, APNSToken(to.token.token))
      case Android =>
        android.push(notification, GCMToken(to.token.token))
      case Unknown(name) =>
        log.error(s"Unsupported device: '$name'. Ignoring push request.")
        Future.successful(PushSummary.empty)
    }
}
