package com.malliina.boat.push

import cats.effect.{ContextShift, IO}

import java.nio.file.Paths
import com.malliina.boat.{APNSConf, PushToken}
import com.malliina.boat.push.APNSPush.log
import com.malliina.push.apns._
import com.malliina.util.AppLogger
import play.api.libs.json.Json

trait APNS {
  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary]
}

object NoopAPNS extends APNS {
  override def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] =
    IO.pure(PushSummary.empty)
}

object APNSPush {
  private val log = AppLogger(getClass)

  def apply(sandbox: APNSTokenClient, prod: APNSTokenClient)(implicit
    cs: ContextShift[IO]
  ): APNSPush =
    new APNSPush(sandbox, prod)

  def apply(conf: APNSConf)(implicit cs: ContextShift[IO]): APNS =
    if (conf.enabled) {
      val confModel = APNSTokenConf(Paths.get(conf.privateKey), conf.keyId, conf.teamId)
      log.info(
        s"Initializing APNS with team ID ${confModel.teamId.team} and private key at ${confModel.privateKey}..."
      )
      val sandbox = APNSTokenClient(confModel, isSandbox = true)
      val prod = APNSTokenClient(confModel, isSandbox = false)
      apply(sandbox, prod)
    } else {
      NoopAPNS
    }
}

class APNSPush(sandbox: APNSTokenClient, prod: APNSTokenClient)(implicit cs: ContextShift[IO])
  extends PushClient[APNSToken]
  with APNS {
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] = {
    val message = APNSMessage
      .simple(notification.message)
      .copy(data = Map("meta" -> Json.toJson(notification)))
    val request = APNSRequest.withTopic(topic, message)
    def pushSandbox = push(to, request, isProd = false)
    def pushProd = push(to, request, isProd = true)
    import cats.implicits._
    (pushSandbox, pushProd).parMapN(_ ++ _)
  }

  def push(to: APNSToken, request: APNSRequest, isProd: Boolean) = {
    val service = if (isProd) prod else sandbox
    IO.fromFuture(IO(service.push(to, request))).map(loggedMap(_, to, useLog = isProd)).map {
      result =>
        if (isProd) {
          PushSummary(
            if (result.error.contains(BadDeviceToken)) Seq(PushToken(result.token.token)) else Nil,
            Nil
          )
        } else {
          PushSummary.empty
        }
    }
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
