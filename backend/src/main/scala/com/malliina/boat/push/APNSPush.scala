package com.malliina.boat.push

import cats.syntax.all.toFunctorOps
import cats.{Applicative, Monad}
import com.malliina.boat.push.APNSPush.log
import com.malliina.boat.{APNSConf, PushToken}
import com.malliina.http.HttpClient
import com.malliina.push.apns.*
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps

import java.nio.file.Paths

trait APNS[F[_]]:
  def push(notification: SourceNotification, to: APNSToken): F[PushSummary]

class NoopAPNS[F[_]: Applicative] extends APNS[F]:
  override def push(notification: SourceNotification, to: APNSToken): F[PushSummary] =
    Applicative[F].pure(PushSummary.empty)

object APNSPush:
  private val log = AppLogger(getClass)

  def fromConf[F[_]: Monad](conf: APNSConf, http: HttpClient[F]): APNS[F] =
    if conf.enabled then
      val confModel = APNSTokenConf(conf.privateKey, conf.keyId, conf.teamId)
      log.info(
        s"Initializing APNS with team ID ${confModel.teamId} and private key at ${conf.privateKey}..."
      )
      val prep = RequestPreparer.token(confModel)
      val sandbox = new APNSHttpClientF(http, prep, isSandbox = true)
      val prod = new APNSHttpClientF(http, prep, isSandbox = false)
      APNSPush(sandbox, prod)
    else
      log.info(s"APNS is disabled.")
      NoopAPNS[F]

class APNSPush[F[_]: Monad](sandbox: APNSHttpClientF[F], prod: APNSHttpClientF[F])
  extends PushClient[F, APNSToken]
  with APNS[F]:
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: SourceNotification, to: APNSToken): F[PushSummary] =
    val message = APNSMessage(
      APSPayload(
        Option(Right(AlertPayload(notification.message, title = Option(notification.title))))
      ),
      Map("meta" -> notification.asJson)
    )
    val request = APNSRequest.withTopic(topic, message)
    push(to, request, isProd = true)

  def push(to: APNSToken, request: APNSRequest, isProd: Boolean): F[PushSummary] =
    val service = if isProd then prod else sandbox
    service
      .push(to, request)
      .map(loggedMap(_, to, useLog = isProd))
      .map: result =>
        if isProd then
          PushSummary(
            if result.error.isEmpty then Seq(result.token) else Nil,
            if result.error.exists(err => PushSummary.removableErrors.exists(_ == err)) then
              Seq(PushToken(result.token.token))
            else Nil,
            Nil
          )
        else PushSummary.empty

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
