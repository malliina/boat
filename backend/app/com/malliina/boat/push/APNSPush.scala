package com.malliina.boat.push

import java.nio.file.Paths

import com.malliina.boat.PushToken
import com.malliina.boat.push.APNSPush.log
import com.malliina.concurrent.Execution.cached
import com.malliina.push.apns._
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

import scala.concurrent.Future

trait APNS {
  def push(notification: BoatNotification, to: APNSToken): Future[PushSummary]
}

object NoopAPNS extends APNS {
  override def push(notification: BoatNotification, to: APNSToken): Future[PushSummary] =
    Future.successful(PushSummary.empty)
}

object APNSPush {
  private val log = Logger(getClass)

  def apply(sandbox: APNSTokenClient, prod: APNSTokenClient): APNSPush =
    new APNSPush(sandbox, prod)

  def apply(conf: Configuration): APNS = {
    val apnsConf = conf.get[Configuration]("boat.push.apns")
    val isEnabled = apnsConf.get[Boolean]("enabled")
    if(isEnabled) {
      val sandbox = APNSTokenClient(tokenConf(apnsConf), isSandbox = true)
      val prod = APNSTokenClient(tokenConf(apnsConf), isSandbox = false)
      apply(sandbox, prod)
    } else {
      NoopAPNS
    }
  }

  private def tokenConf(conf: Configuration): APNSTokenConf = APNSTokenConf(
    Paths.get(conf.get[String]("privateKey")),
    KeyId(conf.get[String]("keyId")),
    TeamId(conf.get[String]("teamId"))
  )
}

class APNSPush(sandbox: APNSTokenClient, prod: APNSTokenClient) extends PushClient[APNSToken] with APNS {
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: BoatNotification, to: APNSToken): Future[PushSummary] = {
    val message = APNSMessage
      .simple(notification.message)
      .copy(data = Map("meta" -> Json.toJson(notification)))
    val request = APNSRequest.withTopic(topic, message)
    val pushSandbox =
      sandbox.push(to, request).map(loggedMap(_, to, useLog = false)).map(_ => PushSummary.empty)
    val pushProd = prod.push(to, request).map(loggedMap(_, to, useLog = true)).map { result =>
      PushSummary(
        if (result.error.contains(BadDeviceToken)) Seq(PushToken(result.token.token)) else Nil,
        Nil
      )
    }
    Future.sequence(Seq(pushSandbox, pushProd)).map(_.fold(PushSummary.empty)(_ ++ _))
  }

  private def loggedMap(
    result: Either[APNSError, APNSIdentifier],
    token: APNSToken,
    useLog: Boolean
  ): APNSHttpResult =
    result.fold(
      err => {
        if (useLog) {
          log.error(s"Failed to push to '$token'. ${err.description}")
        }
        APNSHttpResult(token, None, Option(err))
      },
      id => {
        if (useLog) {
          log.info(s"Successfully pushed to '$token'.")
        }
        APNSHttpResult(token, Option(id), None)
      }
    )
}
