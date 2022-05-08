package com.malliina.boat.push

import cats.effect.IO
import com.malliina.boat.push.APNSPush.log
import com.malliina.boat.{APNSConf, PushToken}
import com.malliina.http.HttpClient
import com.malliina.push.apns.*
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps

import java.nio.file.Paths

trait APNS:
  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary]

object NoopAPNS extends APNS:
  override def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] =
    IO.pure(PushSummary.empty)

object APNSPush:
  private val log = AppLogger(getClass)

  def fromConf(conf: APNSConf, http: HttpClient[IO]): APNS =
    if conf.enabled then
      val confModel = APNSTokenConf(Paths.get(conf.privateKey), conf.keyId, conf.teamId)
      log.info(
        s"Initializing APNS with team ID ${confModel.teamId} and private key at ${conf.privateKey}..."
      )
      val prep = RequestPreparer.token(confModel)
      val sandbox = new APNSHttpClientF(http, prep, isSandbox = true)
      val prod = new APNSHttpClientF(http, prep, isSandbox = false)
      APNSPush(sandbox, prod)
    else
      log.info(s"APNS is disabled.")
      NoopAPNS

class APNSPush(sandbox: APNSHttpClientF[IO], prod: APNSHttpClientF[IO])
  extends PushClient[APNSToken]
  with APNS:
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: BoatNotification, to: APNSToken): IO[PushSummary] =
    val message = APNSMessage
      .simple(notification.message)
      .copy(data = Map("meta" -> notification.asJson))
    val request = APNSRequest.withTopic(topic, message)
    push(to, request, isProd = true)

  def push(to: APNSToken, request: APNSRequest, isProd: Boolean): IO[PushSummary] =
    val service = if isProd then prod else sandbox
    service.push(to, request).map(loggedMap(_, to, useLog = isProd)).map { result =>
      if isProd then
        PushSummary(
          if result.error.contains(BadDeviceToken) then Seq(PushToken(result.token.token)) else Nil,
          Nil
        )
      else PushSummary.empty
    }

  private def loggedMap(
    result: Either[APNSError, APNSIdentifier],
    token: APNSToken,
    useLog: Boolean
  ): APNSHttpResult =
    result.fold(
      err =>
        if useLog then log.error(s"Failed to push to '$token'. ${err.description}")
        APNSHttpResult(token, None, Option(err))
      ,
      id =>
        if useLog then log.info(s"Successfully pushed to '$token'.")
        APNSHttpResult(token, Option(id), None)
    )
