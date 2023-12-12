package com.malliina.boat.push

import cats.syntax.all.toFunctorOps
import cats.{Applicative, Monad}
import com.malliina.boat.APNSConf
import com.malliina.http.HttpClient
import com.malliina.push.apns.*
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps

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
      val prod = new APNSHttpClientF(http, prep, isSandbox = false)
      APNSPush(prod)
    else
      log.info(s"APNS is disabled.")
      NoopAPNS[F]

class APNSPush[F[_]: Monad](prod: APNSHttpClientF[F]) extends PushClient[F, APNSToken] with APNS[F]:
  val topic = APNSTopic("com.malliina.BoatTracker")

  def push(notification: SourceNotification, to: APNSToken): F[PushSummary] =
    val message = APNSMessage(
      APSPayload(
        Option(Right(AlertPayload(notification.message, title = Option(notification.title))))
      ),
      Map("meta" -> notification.asJson)
    )
    val request = APNSRequest.withTopic(topic, message)
    prod
      .push(to, request)
      .map: e =>
        val result = e.fold(
          err => APNSHttpResult(to, None, Option(err)),
          id => APNSHttpResult(to, Option(id), None)
        )
        PushSummary(Seq(result), Nil)
