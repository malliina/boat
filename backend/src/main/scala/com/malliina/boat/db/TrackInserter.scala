package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.implicits.*
import com.malliina.boat.*
import com.malliina.boat.db.TrackInserter.log
import com.malliina.boat.parsing.PointInsert
import com.malliina.database.DoobieDatabase
import com.malliina.measure.{DistanceM, SpeedIntM, SpeedM}
import com.malliina.util.AppLogger
import com.malliina.values.UserId
import doobie.*
import doobie.free.preparedstatement.PreparedStatementIO
import doobie.implicits.*
import doobie.util.log.{LoggingInfo, Parameters}

import scala.annotation.tailrec

object TrackInserter:
  private val log = AppLogger(getClass)

class TrackInserter[F[_]](val db: DoobieDatabase[F]) extends TrackInsertsDatabase[F] with DoobieSQL:
  import db.run
  private val minSpeed: SpeedM = 1.kmh

  private val trackIds = CommonSql.nonEmptyTracksWith(fr0"t.id", None)
  private def trackById(id: TrackId): ConnectionIO[JoinedTrack] =
    sql"${CommonSql.nonEmptyTracks(None)} and t.id = $id".query[JoinedTrack].unique

  private def trackMetas(more: Fragment): Query0[TrackMeta] =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.avg_speed, t.avg_water_temp, t.avg_outside_temp, t.points, t.distance, b.id boatId, b.name boatName, b.source_type, b.token boatToken, u.id userId, u.user, u.email
          from tracks t, boats b, users u
          where t.boat = b.id and b.owner = u.id $more""".query[TrackMeta]

  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): F[JoinedTrack] =
    log.info(s"Updating title of '$track' by user ID $user to '$title'...")
    val trackIO =
      sql"""$trackIds and t.name = $track and b.uid = $user"""
        .query[TrackId]
        .option
        .flatMap(opt => opt.map(pure).getOrElse(fail(TrackNameNotFoundException(track))))
    val canonical = TrackCanonical(Utils.normalize(title))
    def updateIO(tid: TrackId) =
      sql"""update tracks set canonical = $canonical, title = $title
            where id = $tid""".update.run
    updateTrack(trackIO, updateIO)

  def updateComments(track: TrackId, comments: String, user: UserId): F[JoinedTrack] =
    log.info(s"Updating comments of '$track' by user ID $user to '$comments'...")
    val trackIO =
      sql"""$trackIds and t.id = $track and b.uid = $user"""
        .query[TrackId]
        .option
        .flatMap(opt => opt.map(pure).getOrElse(fail(TrackIdNotFoundException(track))))
    def updateIO(tid: TrackId) =
      sql"""update tracks set comments = $comments
            where id = $tid""".update.run
    updateTrack(trackIO, updateIO)

  def addSource(boat: BoatName, sourceType: SourceType, user: UserId): F[SourceRow] = run:
    saveNewSource(boat, sourceType, user, BoatTokens.random()).flatMap(id => boatById(id))

  def removeDevice(device: DeviceId, user: UserId): F[Int] = run:
    sql"delete from boats b where b.id = $device and b.owner = $user".update.run.map: rows =>
      if rows == 1 then log.info(s"Deleted device '$device' owned by '$user'.")
      else log.warn(s"Device '$device' owned by '$user' not found.")
      rows

  def updateBoat(boat: DeviceId, update: PatchBoat, user: UserId): F[SourceRow] = run:
    val ownershipCheck =
      sql"select exists(select b.id from boats b where b.id = $boat and b.owner = $user)"
        .query[Boolean]
        .unique
    for
      exists <- ownershipCheck
      _ <- if exists then pure(()) else fail(BoatNotFoundException(boat, user))
      _ <- update match
        case PatchBoat.ChangeName(name) =>
          sql"update boats set name = ${name.boatName} where id = $boat".update.run
        case PatchBoat.UpdateGps(gps) =>
          sql"update boats set gps_ip = ${gps.ip}, gps_port = ${gps.port} where id = $boat".update.run
      updated <- boatById(boat)
    yield
      val msg = update match
        case PatchBoat.ChangeName(name) => s"Renamed device '$boat' to '${name.boatName}'."
        case PatchBoat.UpdateGps(gps) =>
          s"Updated GPS of boat '${updated.name}' ($boat) to ${gps.describe}."
      log.info(msg)
      updated

  def joinAsSource(meta: DeviceMeta): F[JoinResult] = run:
    val existing: ConnectionIO[List[TrackMeta]] = trackMetas(
      fr"and u.user = ${meta.user} and b.name = ${meta.boat} and t.id in (select p.track from points p where p.added > now() - interval 10 minute)"
    ).to[List]
    existing.flatMap: opt =>
      opt
        .sortBy(_.trackAdded)
        .reverse
        .headOption
        .map: t =>
          log.debug(s"Resuming track ${t.track} for device '${meta.boat}' by '${meta.user}'.")
          pure(JoinResult(t, true))
        .getOrElse:
          val trackMeta = meta.withTrack(TrackNames.random())
          joinSource(trackMeta).flatMap: boat =>
            // Is this necessary?
            trackMetas(fr"and t.name = ${trackMeta.track} and b.id = ${boat.id}").option.flatMap:
              opt =>
                opt
                  .map(m => pure(JoinResult(m, false)))
                  .getOrElse:
                    insertTrack(TrackInput.empty(trackMeta.track, boat.id)).map: meta =>
                      log.info(s"Registered track with ID '${meta.track}' for source '${boat.id}'.")
                      JoinResult(meta, false)

  def saveSentences(sentences: SentencesEvent): F[Seq[KeyedSentence]] = run:
    val from = sentences.from
    val pairs = sentences.sentences.toList.map(s => (s, from.track))
    pairs.toNel
      .map: ps =>
        save(ps).compile.toList.map: list =>
          list.zip(sentences.sentences).map((sk, s) => KeyedSentence(sk, s, from))
      .getOrElse:
        List.empty[KeyedSentence].pure[ConnectionIO]

  private def save(
    pairs: NonEmptyList[(RawSentence, TrackId)]
  ): fs2.Stream[ConnectionIO, SentenceKey] =
    val sql = s"insert into sentences(sentence, track) values (?, ?)"
    Update[(RawSentence, TrackId)](sql).updateManyWithGeneratedKeys[SentenceKey]("id")(pairs.toList)

  def saveCoords(coord: PointInsert): F[InsertedPoint] = run:
    val track = coord.track
    val previous =
      sql"""select coord, track_index
            from points p
            where p.track = $track order by p.track_index desc limit 1"""
        .query[PreviousPoint]
        .option
    for
      prev <- previous
      diff <- prev.map(p => computeDistance(p.coord, coord.coord)).getOrElse(pure(DistanceM.zero))
      point <- insertPoint(coord, prev.map(_.trackIndex).getOrElse(0) + 1, diff)
      avgSpeed <-
        sql"""select avg(speed)
              from points p
              where p.track = $track and p.speed >= $minSpeed
              having avg(speed) is not null"""
          .query[SpeedM]
          .option
      consumption <- sql"""select sum(p1.battery - p2.battery) wattHours
                           from points p1,
                                points p2
                           where p1.track = p2.track
                             and p1.track_index + 1 = p2.track_index
                             and p1.battery - p2.battery > 0
                             and p1.track = $track
                             having sum(p1.battery - p2.battery) is not null""".query[Energy].option
      info <-
        sql"""select avg(water_temp), avg(outside_temperature), ifnull(sum(diff), 0), count(*)
              from points p
              where p.track = $track"""
          .query[DbTrackInfo]
          .option
          .map(_.getOrElse(DbTrackInfo(None, None, DistanceM.zero, 0)))
      _ <-
        sql"""update tracks
              set avg_water_temp = ${info.avgWaterTemp}, avg_outside_temp = ${info.avgOutsideTemp}, avg_speed = $avgSpeed, points = ${info.points}, distance = ${info.distance}, consumption = $consumption
              where id = $track""".update.run
      _ <- insertSentencePoints(
        coord.boatStats.toList.flatMap(_.parts).map(key => (key, point))
      )
      _ = log.debug(s"Inserted $point to $track, fetching by that ID...")
      ref <- trackById(track)
    yield InsertedPoint(point, ref)

  private def insertSentencePoints(
    rows: List[(SentenceKey, TrackPointId)]
  ): ConnectionIO[List[(SentenceKey, TrackPointId)]] =
    rows.toNel
      .map: _ =>
        val params = rows.map(_ => "(?,?)").mkString(",")
        val sql = s"insert into sentence_points(sentence, point) values$params"
        HC.stream[(SentenceKey, TrackPointId)](
          FC.prepareStatement(sql, List("sentence", "point").toArray),
          makeSpParams(rows),
          FPS.executeUpdate *> FPS.getGeneratedKeys,
          chunkSize = 512,
          loggingInfo = LoggingInfo(sql, Parameters.Batch(() => List(Nil)), "insert")
        ).compile
          .toList
      .getOrElse:
        List.empty[(SentenceKey, TrackPointId)].pure[ConnectionIO]

  @tailrec
  private def makeSpParams(
    pairs: List[(SentenceKey, TrackPointId)],
    acc: PreparedStatementIO[Unit] = ().pure[PreparedStatementIO],
    pos: Int = 0
  ): PreparedStatementIO[Unit] =
    pairs match
      case (s, t) :: tail =>
        val nextAcc = acc *> HPS.set(pos + 1, s) *> HPS.set(pos + 2, t)
        makeSpParams(tail, nextAcc, pos + 2)
      case Nil =>
        acc

  private def insertPoint(
    c: PointInsert,
    atIndex: Int,
    diff: DistanceM
  ): ConnectionIO[TrackPointId] =
    val b = c.boatStats
    val car = c.carStats
    sql"""insert into points(longitude, latitude, coord, speed, water_temp, depthm, depth_offsetm, altitude, accuracy, bearing, bearing_accuracy, battery, car_range, outside_temperature, night_mode, source_time, track, track_index, diff, user_agent)
          values(${c.lng},
            ${c.lat},
            ${c.coord},
            ${c.speedOpt},
            ${b.map(_.waterTemp)},
            ${b.map(_.depth)},
            ${b.map(_.depthOffset)},
            ${car.flatMap(_.altitude)},
            ${car.flatMap(_.accuracy)},
            ${car.flatMap(_.bearing)},
            ${car.flatMap(_.bearingAccuracyDegrees)},
            ${car.flatMap(_.batteryLevel)},
            ${car.flatMap(_.rangeRemaining)},
            ${car.flatMap(_.outsideTemperature)},
            ${car.flatMap(_.nightMode)},
            ${c.sourceTime},
            ${c.track},
            $atIndex,
            $diff,
            ${c.userAgent})""".update
      .withUniqueGeneratedKeys[TrackPointId]("id")

  def dates(track: TrackId): ConnectionIO[List[DateVal]] =
    sql"""select distinct(date(source_time))
          from points p
          where p.track = $track""".query[DateVal].to[List]

  def changeTrack(old: TrackId, date: DateVal, newTrack: TrackId): ConnectionIO[Int] =
    sql"""update points set track = $newTrack
          where track = $old and date(source_time) = $date""".update.run

  def insertTrack(in: TrackInput): ConnectionIO[TrackMeta] =
    sql"""insert into tracks(name, boat, avg_speed, avg_water_temp, points, distance, canonical)
          values(${in.name}, ${in.boat}, ${in.avgSpeed}, ${in.avgWaterTemp}, ${in.points}, ${in.distance}, ${in.canonical})""".update
      .withUniqueGeneratedKeys[TrackId]("id")
      .flatMap(id => trackMetas(fr"and t.id = $id").unique)

  private def joinSource(meta: SourceTrackMeta): ConnectionIO[SourceRow] =
    val user = sql"select id from users u where u.user = ${meta.user}".query[UserId].unique
    def existingBoat(uid: UserId) =
      sql"select ${SourceRow.columns} from boats b where b.name = ${meta.boat} and b.owner = $uid"
        .query[SourceRow]
        .option
    user.flatMap: uid =>
      existingBoat(uid).flatMap: opt =>
        opt
          .map(pure)
          .getOrElse:
            sql"select exists(select id from boats where name = ${meta.boat})"
              .query[Boolean]
              .unique
              .flatMap: exists =>
                if exists then fail(BoatNameNotAvailableException(meta.boat, meta.user))
                else
                  saveNewSource(meta.boat, meta.sourceType, uid, BoatTokens.random()).flatMap: id =>
                    boatById(id)

  private def saveNewSource(
    name: BoatName,
    sourceType: SourceType,
    user: UserId,
    token: BoatToken
  ): ConnectionIO[DeviceId] =
    sql"""insert into boats(name, source_type, owner, token) values($name, $sourceType, $user, $token)""".update
      .withUniqueGeneratedKeys[DeviceId]("id")
      .map: id =>
        log.info(s"Registered ${sourceType.name} '$name' with ID '$id' owned by '$user'.")
        id

  private def updateTrack(
    tid: ConnectionIO[TrackId],
    update: TrackId => ConnectionIO[Int]
  ): F[JoinedTrack] = db.run:
    for
      id <- tid
      _ <- update(id)
      updated <- trackById(id)
    yield
      log.info(
        s"Updated track ${updated.track} ('${updated.trackName}') of '${updated.boatName}' by '${updated.user}'."
      )
      updated
