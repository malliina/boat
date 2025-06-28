package com.malliina.boat.push

import com.malliina.boat.db.{PushDevice, PushInput}
import com.malliina.boat.geo.ReverseGeocode
import com.malliina.boat.{Coord, PhoneId, PushId, PushLang, PushToken, TrackMeta, TrackName}
import com.malliina.measure.DistanceM
import com.malliina.values.UserId
import io.circe.Codec

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

case class PushGeo(geocode: Option[ReverseGeocode], image: Option[String]) derives Codec.AsObject

object PushGeo:
  val empty = PushGeo(None, None)

case class PushState(
  track: TrackMeta,
  state: SourceState,
  distance: DistanceM,
  duration: FiniteDuration,
  isResumed: Boolean,
  at: Option[Coord],
  lang: PushLang,
  now: Instant
)

trait PushService[F[_]]:
  def enable(input: PushInput): F[PushId]
  def disable(token: PushToken, user: UserId): F[Boolean]
  def startedActivity(trackName: TrackName, phoneId: PhoneId): F[PushDevice]
  def push(
    state: PushState,
    geo: PushGeo
  ): F[PushSummary]
