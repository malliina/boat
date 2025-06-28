package com.malliina.boat.push

import cats.effect.Async
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps, toTraverseOps}
import com.malliina.boat.db.{DoobieSQL, PushDevice, PushOutcome, TracksSource}
import com.malliina.boat.geo.{Geocoder, ImageApi}
import com.malliina.boat.push.LiveActivityManager.log
import com.malliina.boat.{AppConf, DeviceId, JoinedTrack, PushLang, PushTokenType, SourceType, TrackName}
import com.malliina.database.DoobieDatabase
import com.malliina.tasks.runInBackground
import com.malliina.util.AppLogger
import doobie.implicits.given

import java.time.Instant
import scala.concurrent.duration.DurationInt

object LiveActivityManager:
  private val log = AppLogger(getClass)

class LiveActivityManager[F[_]: Async](
  push: PushEndpoint[F],
  tracks: TracksSource[F],
  geo: Geocoder[F],
  images: ImageApi[F],
  db: DoobieDatabase[F]
) extends DoobieSQL:

  def polling = stream.runInBackground

  private def stream = fs2.Stream
    .awakeEvery[F](1.minutes)
    .evalMap: _ =>
      endSilentActivities(Instant.now()).handleError: err =>
        log.error(s"Failed to end silent activities.", err)
        PushSummary.empty

  private def endSilentActivities(now: Instant) =
    for
      targets <- querySilents
      results <- targets.traverse: target =>
        endActivity(target, now)
      endeds = results.flatten
    yield
      if endeds.nonEmpty then log.info(s"Ended ${endeds.size} silent activities.")
      PushSummary.merge(endeds.map(_._2))

  /** @return
    *   Active update tokens for which there are no updates for the last 2 minutes.
    */
  private def querySilents = db.run:
    sql"""select pc.id, pc.token, pc.device, pc.device_id, pc.live_activity, pc.user, pc.added
          from push_clients pc
          where pc.device = ${PushTokenType.IOSActivityUpdate}
            and pc.live_activity is not null
            and pc.active
            and (pc.id in (select h.client
                           from push_history h
                           where h.client not in (select h2.client from push_history h2 where h2.outcome = ${PushOutcome.Ended})
                           group by h.client
                           having timestampdiff(second, max(h.added), now()) > 120) or
                 pc.id not in (select client from push_history where client is not null))"""
      .query[PushDevice]
      .to[List]

  private def endActivity(target: PushDevice, now: Instant): F[Option[(JoinedTrack, PushSummary)]] =
    target.liveActivityId
      .map: t =>
        tracks
          .details(t)
          .flatMap: optTrack =>
            optTrack
              .map: ref =>
                val coord = ref.tip.coord
                for
                  pos <- geo.reverseGeocode(coord)
                  image <- images.imageEncoded(coord)
                  n = SourceNotification(
                    if ref.boat.sourceType == SourceType.Vehicle then AppConf.CarName
                    else AppConf.Name,
                    ref.boatName,
                    ref.trackName,
                    SourceState.Disconnected,
                    ref.distance,
                    ref.duration,
                    Option(coord),
                    geo = PushGeo(pos, image),
                    lang = PushLang(ref.language)
                  )
                  s <- push.push(n, target, now)
                  _ <- deactivate(target, ref.boatId, ref.trackName)
                yield Option((ref, s))
              .getOrElse:
                Async[F].pure(None)
      .getOrElse:
        Async[F].pure(None)

  private def deactivate(target: PushDevice, device: DeviceId, trackName: TrackName) = db.run:
    val outcome: PushOutcome = PushOutcome.Ended
    sql"""insert into push_history(device, client, live_activity, outcome) values($device, ${target.id}, $trackName, $outcome)""".update.run
      .map: rows =>
        log.info(s"Ended live activity '$trackName' for boat '$device' by '${target.user}'.")
        rows
