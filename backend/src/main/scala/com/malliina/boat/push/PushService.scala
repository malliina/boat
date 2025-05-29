package com.malliina.boat.push

import com.malliina.boat.db.PushInput
import com.malliina.boat.{PushId, PushLang, PushToken, ReverseGeocode, TrackMeta}
import com.malliina.measure.DistanceM
import com.malliina.values.UserId

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait PushService[F[_]]:
  def enable(input: PushInput): F[PushId]
  def disable(token: PushToken, user: UserId): F[Boolean]
  def push(
    device: TrackMeta,
    state: SourceState,
    distance: DistanceM,
    duration: FiniteDuration,
    geo: Option[ReverseGeocode],
    lang: PushLang,
    now: Instant
  ): F[PushSummary]
