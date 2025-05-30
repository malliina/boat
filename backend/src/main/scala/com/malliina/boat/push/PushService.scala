package com.malliina.boat.push

import com.malliina.boat.db.PushInput
import com.malliina.boat.{Coord, PushId, PushLang, PushToken, ReverseGeocode, TrackMeta}
import com.malliina.measure.DistanceM
import com.malliina.values.UserId

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

case class PushState(
  device: TrackMeta,
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
  def push(state: PushState, geo: Option[ReverseGeocode]): F[PushSummary]
