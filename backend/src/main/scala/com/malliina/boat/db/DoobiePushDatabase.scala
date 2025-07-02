package com.malliina.boat.db

import cats.effect.Async
import cats.syntax.all.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, catsSyntaxList, toFlatMapOps, toFunctorOps, toTraverseOps}
import com.malliina.boat.PushTokenType.{IOSActivityStart, IOSActivityUpdate}
import com.malliina.boat.db.DoobiePushDatabase.{PushStatus, log}
import com.malliina.boat.db.Values.RowsChanged
import com.malliina.boat.geo.{Geocoder, ImageApi}
import com.malliina.boat.push.*
import com.malliina.boat.{AppConf, DeviceId, PhoneId, PushId, PushToken, PushTokenType, SourceType, TrackName}
import com.malliina.database.DoobieDatabase
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.Fragments
import doobie.implicits.*
import doobie.util.update.Update

object DoobiePushDatabase:
  private val log = AppLogger(getClass)

  case class PushStatus(id: PushId, active: Boolean)

class DoobiePushDatabase[F[_]: Async](
  val db: DoobieDatabase[F],
  val push: PushEndpoint[F],
  geocoder: Geocoder[F],
  images: ImageApi[F]
) extends PushService[F]
  with DoobieSQL:
  val F = Async[F]

  def enable(input: PushInput): F[PushId] = db.run:
    val user = input.user
    val token = input.token
    val existing = sql"""select id, active
                         from push_clients
                         where token = $token and user = $user"""
      .query[PushStatus]
      .option
    existing.flatMap: idOpt =>
      idOpt
        .map: status =>
          log.info(
            s"Already registered ${input.device} token '$token' for push notifications."
          )
          if status.active then pure(status.id)
          else
            sql"""update push_clients set active = true, device = ${input.device}, device_id = ${input.deviceId}, live_activity = ${input.liveActivityId}
                  where token = $token and user = $user""".update.run
              .map: _ =>
                status.id
        .getOrElse:
          val deactivation = Fragments
            .orOpt(
              input.deviceId
                .filter(_ => input.device == IOSActivityStart)
                .map(phoneId => fr"device_id = $phoneId"),
              input.liveActivityId
                .filter(_ => input.device == IOSActivityUpdate)
                .map(track => fr"device_id = ${input.deviceId} and live_activity = $track")
            )
            .map: where =>
              sql"""update push_clients set active = false
                    where user = $user and device = ${input.device} and ($where)""".update.run
            .getOrElse(pure(0))
          val insertion =
            sql"""insert into push_clients(token, device, device_id, live_activity, user)
                  values($token, ${input.device}, ${input.deviceId}, ${input.liveActivityId}, $user)""".update
              .withUniqueGeneratedKeys[PushId]("id")
          for
            olds <- deactivation
            id <- insertion
          yield
            val describeOlds = if olds > 0 then s"Deactivated $olds old tokens." else ""
            val describeActivity = input.liveActivityId.fold("")(id => s"Live Activity ID '$id'.")
            val basic =
              s"Enabled ${input.device} notifications for user $user with token '$token'."
            val msg =
              Seq(basic, describeOlds, describeActivity)
                .filter(_.nonEmpty)
                .mkString(" ")
            log.info(msg)
            id

  def disable(token: PushToken, user: UserId): F[Boolean] = db.run:
    sql"update push_clients set active = false where token = $token and user = $user".update.run
      .map: rows =>
        if rows > 0 then
          log.info(s"Disabled notifications for token '$token'.")
          true
        else
          log.warn(s"Tried to disable notifications for '$token', but no changes were made.")
          false

  def startedActivity(trackName: TrackName, phoneId: PhoneId): F[PushDevice] = db.run:
    sql"""select id, token, device, device_id, live_activity, user, added
          from push_clients pc
          where pc.device = ${PushTokenType.IOSActivityStart}
            and pc.active
            and pc.device_id = $phoneId
            and pc.id in (select h.client from push_history h where h.live_activity = $trackName)
            and not pc.id in (select h.client from push_history h where h.live_activity = $trackName and h.outcome = ${PushOutcome.Ended})
          order by added desc
          limit 1"""
      .query[PushDevice]
      .unique

  /** Pushes at most once every five minutes to a given device.
    */
  def push(state: PushState): F[PushSummary] =
    val track = state.track
    val title = if track.sourceType == SourceType.Vehicle then AppConf.CarName else AppConf.Name
    val notification =
      SourceNotification(
        title,
        track.deviceName,
        track.trackName,
        state.state,
        state.distance,
        state.duration,
        state.at,
        state.lang
      )
    val boatId = track.device
    val activityStart = PushTokenType.IOSActivityStart
    val activityUpdate = PushTokenType.IOSActivityUpdate
    val devices = db.run:
      // pushes at most once every five minutes as per the "not exists" clause
      sql"""select id, token, device, device_id, live_activity, user, added
            from push_clients
            where user = ${track.userId} and active
              and not device = $activityUpdate
              and not device = $activityStart
              and not ${state.isResumed}
              and not exists(select h.id
                             from push_history h
                             where h.device = $boatId
                             having timestampdiff(SECOND, max(h.added), now()) < 300)"""
        .query[PushDevice]
        .to[List]
    // At most once every 20 seconds
    val liveActivityDevices = db.run:
      sql"""select id, token, device, device_id, live_activity, user, added
            from push_clients
            where user = ${track.userId} and active
              and ((device = $activityUpdate
                    and live_activity = ${track.trackName}
                    and not device_id in (select pc.device_id
                                          from push_clients pc join push_history h on pc.id = h.client
                                          where pc.device = $activityUpdate and pc.live_activity = ${track.trackName}
                                          having timestampdiff(SECOND, max(h.added), now()) < 20)
                    )
                or (device = $activityStart
                    and not device_id in (select pc.device_id
                                          from push_clients pc join push_history h on pc.id = h.client
                                          where pc.device = $activityStart and h.live_activity = ${track.trackName})))
              """
        .query[PushDevice]
        .to[List]
    def bookkeeping(ds: Seq[PushDevice]) = db.run:
      val rows = ds.map(d => (boatId, d.id, track.trackName))
      Update[(DeviceId, PushId, TrackName)](
        s"insert into push_history(device, client, live_activity) values(?, ?, ?)"
      )
        .updateMany(rows)
        .map: rows =>
          log.debug(s"Saved $rows push history rows for device '$boatId' (${track.deviceName}).")
          RowsChanged(rows)
    for
      tokens <- devices
      liveActivityTokens <- liveActivityDevices
      pushable = liveActivityTokens ++ tokens.filterNot(t =>
        t.phoneId.exists(pid => liveActivityTokens.exists(_.phoneId.contains(pid)))
      )
      geo <- state.at
        .map: coord =>
          for
            image <-
              if liveActivityTokens.nonEmpty then images.imageEncoded(coord) else F.pure(None)
            reverse <- geocoder
              .reverseGeocode(coord)
              .handleErrorWith: e =>
                F.delay(log.error(s"Geo lookup of $coord failed.", e)) >> F.pure(None)
          yield PushGeo(reverse, image)
        .getOrElse:
          F.pure(PushGeo.empty)
      results <- pushable.traverse: target =>
        push
          .push(notification, geo, target, state.now)
      summary = results.fold(PushSummary.empty)(_ ++ _)
      _ <- handle(summary)
      _ <- if pushable.nonEmpty then bookkeeping(pushable).void else F.unit
    yield summary

  private def handle(summary: PushSummary): F[Int] =
    if summary.iosSuccesses.nonEmpty then
      log.debug(s"Successfully notified iOS tokens ${summary.iosSuccesses.mkString(", ")}.")
    if summary.gcmSuccesses.nonEmpty then
      log.info(s"Successfully notified GCM tokens ${summary.gcmSuccesses.mkString(", ")}.")
    if summary.noBadTokensOrReplacements then F.pure(0)
    else
      db.run:
        val deleteIO = summary.badTokens.toList.toNel
          .map: bad =>
            val inClause = Fragments.in(fr"token", bad)
            sql"update push_clients set active = false where $inClause".update.run.map:
              deactivated =>
                if deactivated > 0 then
                  log.info(
                    s"Deactivated $deactivated bad tokens: ${summary.badTokens.mkString(", ")}"
                  )
                deactivated
          .getOrElse:
            pure(0)
        val updateIO = summary.replacements.toList
          .traverse: repl =>
            sql"""update push_clients
                  set token = ${repl.newToken}
                  where token = ${repl.oldToken} and device = ${repl.device}""".update.run
              .map: updated =>
                if updated > 0 then
                  log.info(s"Updated token to '${repl.newToken}' from '${repl.oldToken}'.")
                updated
          .map(_.sum)
        deleteIO.flatMap(r1 => updateIO.map(r2 => r1 + r2))
