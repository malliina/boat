package com.malliina.boat.push

import java.nio.file.Paths

import com.malliina.boat.push.PushService.log
import com.malliina.concurrent.Execution.cached
import com.malliina.push.apns._
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import scala.concurrent.Future

object PushService {
  private val log = Logger(getClass)

  def apply(sandbox: APNSTokenClient, prod: APNSTokenClient): PushService =
    new PushService(sandbox, prod)

  def apply(conf: Configuration): PushService = {
    val sandbox = APNSTokenClient(apnsConf(conf), isSandbox = true)
    val prod = APNSTokenClient(apnsConf(conf), isSandbox = false)
    apply(sandbox, prod)
  }

  def apnsConf(conf: Configuration): APNSTokenConf = APNSTokenConf(
    Paths.get(conf.get[String]("boat.push.apns.privateKey")),
    KeyId(conf.get[String]("boat.push.apns.keyId")),
    TeamId(conf.get[String]("boat.push.apns.teamId"))
  )
}

class PushService(sandbox: APNSTokenClient, prod: APNSTokenClient) extends PushSystem {
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: BoatNotification, to: APNSToken): Future[Seq[APNSHttpResult]] = {
    val message = APNSMessage.simple(s"${notification.boatName} ${notification.state}")
      .copy(data = Map("meta" -> Json.toJson(notification)))
    val request = APNSRequest.withTopic(topic, message)
    val pushSandbox = sandbox.push(to, request).map(fold(_, to, useLog = false))
    val pushProd = prod.push(to, request).map(fold(_, to, useLog = true))
    Future.sequence(Seq(pushSandbox, pushProd))
  }

  private def fold(result: Either[APNSError, APNSIdentifier], token: APNSToken, useLog: Boolean): APNSHttpResult =
    result.fold(
      err => {
        if (useLog) {
          log.error(s"Failed to push to '$token'. ${err.description}")
        }
        APNSHttpResult(token, None, Option(err))
      },
      id => {
        if (useLog) {
          log.info(s"Pushed to '$token'.")
        }
        APNSHttpResult(token, Option(id), None)
      }
    )
}
