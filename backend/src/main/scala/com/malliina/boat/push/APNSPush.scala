package com.malliina.boat.push

import cats.syntax.all.toFunctorOps
import cats.{Applicative, Monad}
import com.malliina.boat.APNSConf
import com.malliina.http.HttpClient
import com.malliina.push.apns.*
import com.malliina.storage.StorageInt
import com.malliina.util.AppLogger
import io.circe.syntax.EncoderOps

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.concurrent.duration.{DurationInt, FiniteDuration}

enum APSEventType:
  case Start, Update, End

trait APNS[F[_]]:
  def push(notification: SourceNotification, to: APNSToken): F[PushSummary]
  def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    now: Instant
  ): F[PushSummary]

class NoopAPNS[F[_]: Applicative] extends APNS[F]:
  override def push(notification: SourceNotification, to: APNSToken): F[PushSummary] =
    noop

  override def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    now: Instant
  ): F[PushSummary] = noop

  def noop = Applicative[F].pure(PushSummary.empty)

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
  import APNSPush.log
  val topic = APNSTopic("com.malliina.BoatTracker")

  val attributesType = "BoatWidgetAttributes"

  def push(notification: SourceNotification, to: APNSToken): F[PushSummary] =
    val message = APNSMessage(
      APSPayload(
        Option(Right(AlertPayload(notification.message, title = Option(notification.title))))
      ),
      Map("meta" -> notification.asJson)
    )
    val request = APNSRequest.withTopic(topic, message)
    push(request, to)

  override def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    now: Instant
  ): F[PushSummary] =
    val lang = notification.lang
    val payload = event match
      case APSEventType.Start =>
        APSPayload.startLiveActivity(
          now,
          LiveActivityAttributes.attributeType,
          LiveActivityAttributes(notification.boatName, notification.trackName),
          toActivityState(notification, lang.onTheMove),
          Right(AlertPayload(notification.message, title = Option(notification.title))),
          Option(now.plus(5.minutes))
        )
      case APSEventType.Update =>
        APSPayload.updateLiveActivity(
          now,
          toActivityState(notification, lang.onTheMove),
          alert = None,
          staleDate = Option(now.plus(5.minutes)),
          dismissalDate = None
        )
      case APSEventType.End =>
        APSPayload.endLiveActivity(
          now,
          toActivityState(notification, lang.stoppedMoving),
          dismissalDate = Option(now.plus(5.minutes))
        )
    val message = APNSMessage(payload, Map("meta" -> notification.asJson))
    val request = APNSRequest.liveActivity(topic, message)
    push(request, to).map: s =>
      val stats =
        s"${notification.distance} after ${notification.duration} of track '${notification.trackName}'"
      log.info(s"Pushed $event event '${notification.message}' of $stats to '$to'.")
      s

  private def toActivityState(notification: SourceNotification, message: String) =
    LiveActivityState(
      message,
      notification.distance,
      notification.duration,
      notification.geo.geocode.map(_.address),
      notification.coord,
      notification.geo.image
    )

  def push(request: APNSRequest, to: APNSToken): F[PushSummary] =
    val bytes = request.message.asJson.toString.getBytes(StandardCharsets.UTF_8).length.bytes
    log.info(s"Pushing $bytes to APNS...")
    prod
      .push(to, request)
      .map: e =>
        val result = e.fold(
          err => APNSHttpResult(to, None, Option(err)),
          id => APNSHttpResult(to, Option(id), None)
        )
        PushSummary(Seq(result), Nil)

extension (i: Instant) def plus(duration: FiniteDuration) = i.plusSeconds(duration.toSeconds)
