package com.malliina.boat.push

import cats.syntax.all.toFunctorOps
import cats.{Applicative, Monad}
import com.malliina.boat.{APNSConf, BoatFormats}
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
  def push(notification: SourceNotification, geo: PushGeo, to: APNSToken): F[PushSummary]
  def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    geo: PushGeo,
    now: Instant
  ): F[PushSummary]

class NoopAPNS[F[_]: Applicative] extends APNS[F]:
  override def push(notification: SourceNotification, geo: PushGeo, to: APNSToken): F[PushSummary] =
    noop

  override def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    geo: PushGeo,
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
      val prep = TokenBuilder.token(confModel)
      val prod = new APNSHttpClientF(http, prep, isSandbox = false)
      APNSPush(prod)
    else
      log.info(s"APNS is disabled.")
      NoopAPNS[F]

class APNSPush[F[_]: Monad](prod: APNSHttpClientF[F]) extends PushClient[F, APNSToken] with APNS[F]:
  import APNSPush.log
  val topic = APNSTopic("com.malliina.BoatTracker")

  val attributesType = "BoatWidgetAttributes"

  def push(notification: SourceNotification, geo: PushGeo, to: APNSToken): F[PushSummary] =
    val payload =
      AlertPayload(notification.message(geo.geocode), title = Option(notification.title))
    val message = APNSMessage(
      APSPayload(Option(Right(payload))),
      Map("meta" -> notification.asJson)
    )
    val request = APNSRequest.withTopic(topic, message)
    push(request, to)

  override def pushLiveActivity(
    notification: SourceNotification,
    to: APNSToken,
    event: APSEventType,
    geo: PushGeo,
    now: Instant
  ): F[PushSummary] =
    val lang = notification.lang
    val payload = event match
      case APSEventType.Start =>
        APSPayload.startLiveActivity(
          now,
          LiveActivityAttributes.attributeType,
          LiveActivityAttributes(notification.boatName, notification.trackName),
          toActivityState(notification, lang.onTheMove, geo),
          Right(
            AlertPayload(notification.message(geo.geocode), title = Option(notification.title))
          ),
          Option(now.plus(5.minutes))
        )
      case APSEventType.Update =>
        APSPayload.updateLiveActivity(
          now,
          toActivityState(notification, lang.onTheMove, geo),
          alert = None,
          staleDate = Option(now.plus(5.minutes)),
          dismissalDate = None
        )
      case APSEventType.End =>
        APSPayload.endLiveActivity(
          now,
          toActivityState(notification, lang.stoppedMoving, geo),
          dismissalDate = Option(now.plus(5.minutes))
        )
    val message = APNSMessage(payload, Map("meta" -> notification.asJson))
    val request = APNSRequest.liveActivity(topic, message)
    push(request, to).map: s =>
      val bytes = request.message.asJson.toString.getBytes(StandardCharsets.UTF_8).length.bytes
      val distance = BoatFormats.formatDistance(notification.distance)
      val duration = BoatFormats.durationHuman(notification.duration)
      val stats =
        s"$distance after $duration of track '${notification.trackName}'"
      log.info(
        s"Pushed $event event of $bytes with '${notification.message(geo.geocode)}' of $stats to '$to'."
      )
      s

  private def toActivityState(notification: SourceNotification, message: String, geo: PushGeo) =
    LiveActivityState(
      message,
      notification.distance,
      notification.duration,
      geo.geocode.map(_.address),
      notification.coord,
      geo.image
    )

  def push(request: APNSRequest, to: APNSToken): F[PushSummary] =
    prod
      .push(to, request)
      .map: e =>
        val result = e.fold(
          err => APNSHttpResult(to, None, Option(err)),
          id => APNSHttpResult(to, Option(id), None)
        )
        PushSummary(Seq(result), Nil)

extension (i: Instant) def plus(duration: FiniteDuration) = i.plusSeconds(duration.toSeconds)
