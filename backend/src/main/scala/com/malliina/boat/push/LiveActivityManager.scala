package com.malliina.boat.push

import cats.effect.Async
import cats.syntax.all.{toFlatMapOps, toFunctorOps, toTraverseOps}
import com.malliina.boat.db.{DoobieSQL, PushDevice, PushOutcome, TracksSource}
import com.malliina.boat.push.LiveActivityManager.log
import com.malliina.boat.{AppConf, DeviceId, PushLang, SourceType, TrackName}
import com.malliina.database.DoobieDatabase
import com.malliina.util.AppLogger
import doobie.implicits.given

import java.time.Instant
import scala.concurrent.duration.DurationInt

object LiveActivityManager:
  private val log = AppLogger(getClass)

class LiveActivityManager[F[_]: Async](
  push: PushEndpoint[F],
  tracks: TracksSource[F],
  db: DoobieDatabase[F]
) extends DoobieSQL:

  def polling = fs2.Stream.emit(()).concurrently(stream).compile.resource.lastOrError

  private def stream = fs2.Stream
    .awakeEvery[F](5.seconds)
    .evalMap: _ =>
      endSilentActivities(Instant.now())

  private def endSilentActivities(now: Instant) =
    for
      targets <- querySilents
      results <- targets.traverse: target =>
        endActivity(target, now)
      endeds = results.flatten
    yield
      log.info(s"Ended ${endeds.size} silent activities.")
      PushSummary.merge(endeds.map(_._2))

  /** @return
    *   Active update tokens for which there are no updates for the last 5 minutes.
    */
  private def querySilents = db.run:
    sql"""select pc.id, pc.token, pc.device, pc.device_id, pc.live_activity, pc.user, pc.added
              from push_clients pc
                       join (select h.client, max(h.added) latest
                             from push_history h
                             where h.client not in (select h2.client from push_history h2 where h2.outcome = 'ended')
                             group by h.client
                             having timestampdiff(second, max(h.added), now()) > 300) h on pc.id = h.client
              where pc.device = 'ios-activity-update'
                and pc.live_activity is not null
                and pc.active
              order by h.latest desc"""
      .query[PushDevice]
      .to[List]

  private def endActivity(target: PushDevice, now: Instant) =
    target.liveActivityId
      .map: t =>
        tracks
          .details(t)
          .flatMap: ref =>
            val title =
              if ref.boat.sourceType == SourceType.Vehicle then AppConf.CarName else AppConf.Name
            val pushLang = PushLang(ref.language)
            val n = SourceNotification(
              title,
              ref.boatName,
              ref.trackName,
              SourceState.Disconnected,
              ref.distance,
              ref.duration,
              geo = None,
              lang = pushLang
            )
            push
              .push(n, target, now)
              .flatMap: s =>
                deactivate(target, ref.boatId, ref.trackName).map: _ =>
                  Option((ref, s))
      .getOrElse:
        Async[F].pure(None)

  private def deactivate(target: PushDevice, device: DeviceId, trackName: TrackName) = db.run:
    val outcome: PushOutcome = PushOutcome.Ended
    sql"""insert into push_history(device, client, live_activity, outcome) values($device, ${target.id}, $trackName, $outcome)""".update.run
      .map: rows =>
        log.info(s"Ended live activity '$trackName' for boat '$device' by '${target.user}'.")
        rows
