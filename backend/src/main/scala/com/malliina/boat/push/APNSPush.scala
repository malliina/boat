package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.push.APNSPush.log
import com.malliina.boat.{APNSConf, PushToken}
import com.malliina.http.HttpClient
import com.malliina.push.apns._
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps

import java.nio.file.Paths

trait APNS {
  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary]
}

object NoopAPNS extends APNS {
  override def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] =
    IO.pure(PushSummary.empty)
}

object APNSPush {
  private val log = AppLogger(getClass)

  def apply(sandbox: APNSHttpClientF[IO], prod: APNSHttpClientF[IO]): APNSPush =
    new APNSPush(sandbox, prod)

  def apply(conf: APNSConf, http: HttpClient[IO]): APNS =
    if (conf.enabled) {
      val confModel = APNSTokenConf(Paths.get(conf.privateKey), conf.keyId, conf.teamId)
      log.info(
        s"Initializing APNS with team ID ${confModel.teamId} and private key at ${conf.privateKey}..."
      )
      val prep = RequestPreparer.token(confModel)
      val sandbox = new APNSHttpClientF(http, prep, isSandbox = true)
      val prod = new APNSHttpClientF(http, prep, isSandbox = false)
      apply(sandbox, prod)
    } else {
      NoopAPNS
    }
}

class APNSPush(sandbox: APNSHttpClientF[IO], prod: APNSHttpClientF[IO])
  extends PushClient[APNSToken]
  with APNS {
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] = {
    val message = APNSMessage
      .simple(notification.message)
      .copy(data = Map("meta" -> notification.asJson))
    val request = APNSRequest.withTopic(topic, message)
    def pushSandbox = push(to, request, isProd = false)
    def pushProd = push(to, request, isProd = true)
    import cats.implicits._
    pushProd.map2(pushSandbox)(_ ++ _)
  }

  def push(to: APNSToken, request: APNSRequest, isProd: Boolean): IO[PushSummary] = {
    val service = if (isProd) prod else sandbox
    service.push(to, request).map(loggedMap(_, to, useLog = isProd)).map { result =>
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
