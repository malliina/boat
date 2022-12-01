package com.malliina.boat.db

import cats.implicits.*
import cats.data.NonEmptyList
import cats.effect.kernel.implicits.monadCancelOps_
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.boat.db.TrackInserter.log
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.*
import com.malliina.measure.{DistanceM, SpeedIntM, SpeedM}
import com.malliina.values.UserId
import doobie.*
import doobie.implicits.*
import cats.effect.{Async, IO}
import com.malliina.util.AppLogger
import doobie.free.preparedstatement.PreparedStatementIO

import concurrent.duration.DurationLong
import java.time.temporal.ChronoUnit
import scala.annotation.tailrec
import scala.util.Random

object TrackInserter:
  private val log = AppLogger(getClass)

class TrackInserter[F[_]: Async](val db: DoobieDatabase[F])
  extends TrackInsertsDatabase[F]
  with DoobieSQL:
  import DoobieMappings.*
  import db.{run, logHandler}
  val minSpeed: SpeedM = 1.kmh

  private val trackIds = CommonSql.nonEmptyTracksWith(fr0"t.id")
  private def trackById(id: TrackId) =
    sql"${CommonSql.nonEmptyTracks} and t.id = $id".query[JoinedTrack].unique

  private def trackMetas(more: Fragment): Query0[TrackMeta] =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.avg_speed, t.avg_water_temp, t.points, t.distance, b.id boatId, b.name boatName, b.token boatToken, u.id userId, u.user, u.email
          from tracks t, boats b, users u
          where t.boat = b.id and b.owner = u.id $more""".query[TrackMeta]

  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): F[JoinedTrack] =
    log.info(s"Updating title of '$track' by user ID $user to '$title'...")
    val trackIO =
      sql"""$trackIds and t.name = $track and b.uid = $user"""
        .query[TrackId]
        .option
        .flatMap { opt => opt.map(pure).getOrElse(fail(new TrackNameNotFoundException(track))) }
    val canonical = TrackCanonical(Utils.normalize(title.title))
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
        .flatMap { opt => opt.map(pure).getOrElse(fail(new TrackIdNotFoundException(track))) }
    def updateIO(tid: TrackId) =
      sql"""update tracks set comments = $comments
            where id = $tid""".update.run
    updateTrack(trackIO, updateIO)

  def addBoat(boat: BoatName, user: UserId): F[BoatRow] = run {
    saveNewBoat(boat, user, BoatTokens.random()).flatMap { id =>
      boatById(id)
    }
  }

  def removeDevice(device: DeviceId, user: UserId): F[Int] = run {
    sql"delete from boats where owner = $user and id = $device".update.run.map { rows =>
      if rows == 1 then log.info(s"Deleted boat '$device' owned by '$user'.")
      else log.warn(s"Boat '$device' owned by '$user' not found.")
      rows
    }
  }

  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): F[BoatRow] = run {
    val ownershipCheck =
      sql"select exists(${CommonSql.boats} and b.id = $boat and b.owner = $user)"
        .query[Boolean]
        .unique
    for
      exists <- ownershipCheck
      _ <- if exists then pure(42) else fail(new BoatNotFoundException(boat, user))
      _ <- sql"update boats set name = $newName where id = $boat".update.run
      updated <- boatById(boat)
    yield
      log.info(s"Renamed boat '$boat' to '$newName'.")
      updated
  }

  def joinAsBoat(meta: DeviceMeta): F[TrackMeta] = run {
    val existing: ConnectionIO[List[TrackMeta]] = trackMetas(
      fr"and u.user = ${meta.user} and b.name = ${meta.boat} and t.id in (select p.track from points p where p.added > now() - interval 10 minute)"
    ).to[List]
    existing.flatMap { opt =>
      opt
        .sortBy(_.trackAdded)
        .reverse
        .headOption
        .map { t =>
          log.info(
            s"Resuming track ${t.track} for boat '${meta.boat}' by '${meta.user}'. There was probably a temporary connection glitch."
          )
          pure(t)
        }
        .getOrElse {
          val trackMeta = meta.withTrack(TrackNames.random())
          joinBoat(trackMeta).flatMap { boat =>
            // Is this necessary?
            trackMetas(fr"and t.name = ${trackMeta.track} and b.id = ${boat.id}").option.flatMap {
              opt =>
                opt.map(pure).getOrElse {
                  insertTrack(TrackInput.empty(trackMeta.track, boat.id)).map { meta =>
                    log.info(s"Registered track with ID '${meta.track}' for boat '${boat.id}'.")
                    meta
                  }
                }
            }
          }
        }
    }
  }

  def joinAsDevice(from: DeviceMeta): F[JoinedBoat] = run {
    val user = from.user
    val boat = from.boat
    sql"${CommonSql.boats} and b.name = $boat and u.user = $user".query[JoinedBoat].option.flatMap {
      opt =>
        opt.map(pure).getOrElse {
          sql"select id from users u where u.user = $user".query[UserId].unique.flatMap { id =>
            sql"${CommonSql.boats} and b.name = $boat and u.id = $id"
              .query[JoinedBoat]
              .option
              .flatMap { optB =>
                optB.map(pure).getOrElse {
                  sql"select exists(select id from boats b where b.name = $boat)"
                    .query[Boolean]
                    .unique
                    .flatMap[JoinedBoat] { alreadyExists =>
                      if alreadyExists then fail(new BoatNameNotAvailableException(boat, user))
                      else insertBoat(boat, id, BoatTokens.random())
                    }
                }
              }
          }
        }
    }
  }

  def saveSentences(sentences: SentencesEvent): F[Seq[KeyedSentence]] = run {
    val from = sentences.from
    val pairs = sentences.sentences.toList.map { s =>
      (s, from.track)
    }
    pairs.toNel.map { ps =>
      save(ps).compile.toList.map { list =>
        list.zip(sentences.sentences).map { case (sk, s) => KeyedSentence(sk, s, from) }
      }
    }.getOrElse {
      List.empty[KeyedSentence].pure[ConnectionIO]
    }
  }

  private def save(
    pairs: NonEmptyList[(RawSentence, TrackId)]
  ): fs2.Stream[ConnectionIO, SentenceKey] =
    val params = pairs.toList.map(_ => "(?,?)").mkString(",")
    val sql = s"insert into sentences(sentence, track) values$params"
    val prep = makeParams(pairs.toList)
    HC.updateWithGeneratedKeys[SentenceKey](List("id"))(sql, prep, 512)

  @tailrec
  private def makeParams(
    pairs: List[(RawSentence, TrackId)],
    acc: PreparedStatementIO[Unit] = ().pure[PreparedStatementIO],
    pos: Int = 0
  ): PreparedStatementIO[Unit] =
    pairs match
      case (s, t) :: tail =>
        val nextAcc = acc *> HPS.set(pos + 1, s) *> HPS.set(pos + 2, t)
        makeParams(tail, nextAcc, pos + 2)
      case Nil =>
        acc

  def saveCoords(coord: FullCoord): F[InsertedPoint] = run {
    val track = coord.from.track
    val trail =
      sql"""select id, longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff, added 
            from points p 
            where p.track = $track"""
    val previous = sql"$trail order by p.track_index desc limit 1".query[TrackPointRow].option
    for
      prev <- previous
      diff <- prev.map(p => computeDistance(p.coord, coord.coord)).getOrElse(pure(DistanceM.zero))
      point <- insertPoint(coord, prev.map(_.trackIndex).getOrElse(0) + 1, diff)
      avgSpeed <-
        sql"""select avg(boat_speed) 
              from points p 
              where p.track = $track and p.boat_speed >= $minSpeed 
              having avg(boat_speed) is not null"""
          .query[SpeedM]
          .option
      info <-
        sql"""select avg(water_temp), sum(diff), count(*) 
              from points p 
              where p.track = $track 
              having avg(water_temp) is not null"""
          .query[DbTrackInfo]
          .option
      rows <- {
        val avgTemp = info.map(_.avgTemp)
        val points = info.map(_.points).getOrElse(0)
        val distance = info.map(_.distance).getOrElse(DistanceM.zero)
        sql"""update tracks 
              set avg_water_temp = $avgTemp, avg_speed = $avgSpeed, points = $points, distance = $distance 
              where id = $track""".update.run
      }
      _ <- insertSentencePoints(coord.parts.map { key => (key, point) }.toList)
      ref <- trackById(track)
    yield InsertedPoint(point, ref)
  }

  def saveCoordsFast(coord: FullCoord): F[TrackPointId] = run {
    for
      point <- insertPoint(coord, Random.between(1, 1000000), DistanceM.zero)
      _ <- insertSentencePoints(coord.parts.map { key => (key, point) }.toList)
    yield point
  }

  private def insertSentencePoints(
    rows: List[(SentenceKey, TrackPointId)]
  ): ConnectionIO[List[(SentenceKey, TrackPointId)]] =
    rows.toNel.map { rs =>
      val params = rows.map(_ => "(?,?)").mkString(",")
      val sql = s"insert into sentence_points(sentence, point) values$params"
      HC.updateWithGeneratedKeys[(SentenceKey, TrackPointId)](List("sentence", "point"))(
        sql,
        makeSpParams(rows),
        512
      ).compile
        .toList
    }.getOrElse {
      List.empty[(SentenceKey, TrackPointId)].pure[ConnectionIO]
    }

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

  private def insertPoint(c: FullCoord, atIndex: Int, diff: DistanceM): ConnectionIO[TrackPointId] =
    sql"""insert into points(longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff)
          values(${c.lng}, ${c.lat}, ${c.coord}, ${c.boatSpeed}, ${c.waterTemp}, ${c.depth}, ${c.depthOffset}, ${c.boatTime}, ${c.from.track}, $atIndex, $diff)""".update
      .withUniqueGeneratedKeys[TrackPointId]("id")

  def dates(track: TrackId): ConnectionIO[List[DateVal]] =
    sql"""select distinct(date(boat_time)) 
          from points p 
          where p.track = $track""".query[DateVal].to[List]

  def changeTrack(old: TrackId, date: DateVal, newTrack: TrackId): ConnectionIO[Int] =
    sql"""update points set track = $newTrack 
          where track = $old and date(boat_time) = $date""".update.run

  def insertTrack(in: TrackInput): ConnectionIO[TrackMeta] =
    sql"""insert into tracks(name, boat, avg_speed, avg_water_temp, points, distance, canonical) 
          values(${in.name}, ${in.boat}, ${in.avgSpeed}, ${in.avgWaterTemp}, ${in.points}, ${in.distance}, ${in.canonical})""".update
      .withUniqueGeneratedKeys[TrackId]("id")
      .flatMap { id =>
        trackMetas(fr"and t.id = $id").unique
      }

  private def insertBoat(
    boatName: BoatName,
    owner: UserId,
    withToken: BoatToken
  ): ConnectionIO[JoinedBoat] =
    saveNewBoat(boatName, owner, withToken).flatMap { id =>
      CommonSql.boatsById(id)
    }

  private def joinBoat(meta: BoatTrackMeta): ConnectionIO[BoatRow] =
    val user = sql"select id from users u where u.user = ${meta.user}".query[UserId].unique
    def existingBoat(uid: UserId) =
      sql"select id, name, token, owner, added from boats b where b.name = ${meta.boat} and b.owner = $uid"
        .query[BoatRow]
        .option
    user.flatMap { uid =>
      existingBoat(uid).flatMap { opt =>
        opt.map(pure).getOrElse {
          sql"select exists(select id from boats where name = ${meta.boat})"
            .query[Boolean]
            .unique
            .flatMap { exists =>
              if exists then fail(new BoatNameNotAvailableException(meta.boat, meta.user))
              else saveNewBoat(meta.boat, uid, BoatTokens.random()).flatMap { id => boatById(id) }
            }
        }
      }
    }

  private def saveNewBoat(
    name: BoatName,
    user: UserId,
    token: BoatToken
  ): ConnectionIO[DeviceId] =
    sql"""insert into boats(name, owner, token) values($name, $user, $token)""".update
      .withUniqueGeneratedKeys[DeviceId]("id")
      .map { id =>
        log.info(s"Registered boat '$name' with ID '$id' owned by '$user'.")
        id
      }

  private def updateTrack(
    tid: ConnectionIO[TrackId],
    update: TrackId => ConnectionIO[Int]
  ): F[JoinedTrack] = db.run {
    for
      id <- tid
      _ <- update(id)
      updated <- trackById(id)
    yield
      log.info(
        s"Updated track ${updated.track} ('${updated.trackName}') of '${updated.boatName}' by '${updated.user}'."
      )
      updated
  }
