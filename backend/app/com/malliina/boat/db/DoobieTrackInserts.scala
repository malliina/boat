package com.malliina.boat.db

import cats.implicits._
import com.malliina.boat.db.DoobieTrackInserts.log
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{BoatName, BoatToken, BoatTokens, BoatTrackMeta, DateVal, DeviceId, DeviceMeta, InsertedPoint, JoinedBoat, JoinedTrack, KeyedSentence, SentenceKey, SentencesEvent, TrackCanonical, TrackId, TrackInput, TrackMeta, TrackName, TrackPointId, TrackPointRow, TrackTitle, Utils}
import com.malliina.measure.{DistanceM, SpeedIntM, SpeedM}
import com.malliina.values.UserId
import doobie._
import doobie.implicits._
import play.api.Logger
import DoobieTrackInserts.IO
import scala.concurrent.Future

object DoobieTrackInserts {
  private val log = Logger(getClass)
  type IO[A] = ConnectionIO[A]
  def apply(db: DoobieDatabase): DoobieTrackInserts = new DoobieTrackInserts(db)
}

class DoobieTrackInserts(val db: DoobieDatabase) extends TrackInsertsDatabase with DoobieSQL {
  import DoobieMappings._
  val minSpeed: SpeedM = 1.kmh

  private val trackIds = CommonSql.nonEmptyTracksWith(fr0"t.id")
  private def trackById(id: TrackId) =
    sql"${CommonSql.nonEmptyTracks} and t.id = $id".query[JoinedTrack].unique

  private val trackMetas =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.avg_speed, t.avg_water_temp, t.points, t.distance, b.id boatId, b.name boatName, b.token boatToken, u.id userId, u.user, u.email
         from tracks t, boats b, users u
         where t.boat = b.id and b.owner = u.id"""
  def updateTitle(track: TrackName, title: TrackTitle, user: UserId): Future[JoinedTrack] = {
    val trackIO =
      sql"""$trackIds and t.name = $track and b.uid = $user"""
        .query[TrackId]
        .option
        .flatMap { opt => opt.map(pure).getOrElse(fail(s"Track not found: '$track'.")) }
    val canonical = TrackCanonical(Utils.normalize(title.title))
    def updateIO(tid: TrackId) =
      sql"""update tracks set canonical = $canonical, title = $title
            where id = $tid""".update.run
    updateTrack(trackIO, updateIO)
  }

  def updateComments(track: TrackId, comments: String, user: UserId): Future[JoinedTrack] = {
    val trackIO =
      sql"""$trackIds and t.id = $track and b.uid = $user"""
        .query[TrackId]
        .option
        .flatMap { opt => opt.map(pure).getOrElse(fail(s"Track not found: '$track'.")) }
    def updateIO(tid: TrackId) =
      sql"""update tracks set comments = $comments
            where id = $tid""".update.run
    updateTrack(trackIO, updateIO)
  }

  def addBoat(boat: BoatName, user: UserId): Future[BoatRow] = db.run {
    saveNewBoat(boat, user, BoatTokens.random()).flatMap { id =>
      log.info(s"Registered boat '$boat' with ID '$id' owned by '$user'.")
      boatById(id)
    }
  }

  def removeDevice(device: DeviceId, user: UserId): Future[Int] = db.run {
    sql"delete from boats where owner = $user and id = $device".update.run.map { rows =>
      if (rows == 1) log.info(s"Deleted boat '$device' owned by '$user'.")
      else log.warn(s"Boat '$device' owned by '$user' not found.")
      rows
    }
  }

  def renameBoat(boat: DeviceId, newName: BoatName, user: UserId): Future[BoatRow] = db.run {
    val ownershipCheck =
      sql"select exists(${CommonSql.boats} and b.id = $boat and b.owner = $user)"
        .query[Boolean]
        .unique
    for {
      exists <- ownershipCheck
      _ <- if (exists) pure(42) else fail(s"Boat '$boat' by '$user' not found.")
      _ <- sql"update boats set name = $newName where id = $boat".update.run
      updated <- boatById(boat)
    } yield {
      log.info(s"Renamed boat '$boat' to '$newName'.")
      updated
    }
  }

  def joinAsBoat(meta: BoatTrackMeta): Future[TrackMeta] = db.run {
    val existing =
      sql"""$trackMetas and u.user = ${meta.user} and b.name = ${meta.boat} and t.name = ${meta.track}"""
        .query[TrackMeta]
        .option
    existing.flatMap { opt =>
      opt.map(pure).getOrElse {
        joinBoat(meta).flatMap { boat =>
          // Is this necessary?
          sql"$trackMetas and t.name = ${meta.track} and b.id = ${boat.id}"
            .query[TrackMeta]
            .option
            .flatMap { opt =>
              opt.map(pure).getOrElse {
                insertTrack(TrackInput.empty(meta.track, boat.id)).map { meta =>
                  log.info(s"Registered track with ID '${meta.track}' for boat '${boat.id}'.")
                  meta
                }
              }
            }
        }
      }
    }
  }

  def joinAsDevice(from: DeviceMeta): Future[JoinedBoat] = db.run {
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
                      if (alreadyExists) {
                        fail(
                          s"Boat name '$boat' is already taken and therefore not available for '$user'."
                        )
                      } else {
                        insertBoat(boat, id)
                      }
                    }
                }
              }
          }
        }
    }
  }

  def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]] = db.run {
    val from = sentences.from
    sentences.sentences.toList.traverse { s =>
      sql"""insert into sentences(sentence, track) 
            values($s, ${from.track})""".update.withUniqueGeneratedKeys[SentenceKey]("id").map {
        key =>
          KeyedSentence(key, s, from)
      }
    }
  }

  def saveCoords(coord: FullCoord): Future[InsertedPoint] = db.run {
    val track = coord.from.track
    val trail =
      sql"""select id, longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff, added 
            from points p 
            where p.track = $track"""
    val previous = sql"$trail order by p.track_index desc limit 1".query[TrackPointRow].option
    for {
      prev <- previous
      diff <- prev.map(p => computeDistance(p.coord, coord.coord)).getOrElse(pure(DistanceM.zero))
      point <- insertPoint(coord, prev.map(_.trackIndex).getOrElse(0) + 1, diff)
      avgSpeed <-
        sql"select avg(boat_speed) from points p where p.track = $track and p.boat_speed >= $minSpeed"
          .query[SpeedM]
          .unique
      info <-
        sql"select avg(water_temp), sum(diff), count(*) from points p where p.track = $track"
          .query[TrackInfo]
          .unique
      rows <-
        sql"update tracks set avg_water_temp = ${info.avgTemp}, avg_speed = $avgSpeed, points = ${info.points}, distance = ${info.distance} where id = $track".update.run
      parts <- coord.parts.toList.traverse { part =>
        sql"""insert into sentence_points(sentence, point) values($part, $point)""".update.run
      }
      ref <- trackById(track)
    } yield InsertedPoint(point, ref)
  }

  private def insertPoint(c: FullCoord, atIndex: Int, diff: DistanceM) =
    sql"""insert into points(longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff)
         values(${c.lng}, ${c.lat}, ${c.coord}, ${c.boatSpeed}, ${c.waterTemp}, ${c.depth}, ${c.depthOffset}, ${c.boatTime}, ${c.from.track}, $atIndex, $diff)""".update
      .withUniqueGeneratedKeys[TrackPointId]("id")

  def dates(track: TrackId): IO[List[DateVal]] =
    sql"""select distinct(date(boat_time)) 
          from points p 
          where p.track = $track""".query[DateVal].to[List]

  def changeTrack(old: TrackId, date: DateVal, newTrack: TrackId): IO[Int] = {
    sql"""update points set track = $newTrack 
          where track = $old and date(boat_time) = $date""".update.run
  }

  def insertTrack(in: TrackInput): ConnectionIO[TrackMeta] =
    sql"""insert into tracks(name, boat, avg_speed, avg_water_temp, points, distance, canonical) 
         values(${in.name}, ${in.boat}, ${in.avgSpeed}, ${in.avgWaterTemp}, ${in.points}, ${in.distance}, ${in.canonical})""".update
      .withUniqueGeneratedKeys[TrackId]("id")
      .flatMap { id =>
        sql"$trackMetas and t.id = $id".query[TrackMeta].unique
      }

  private def insertBoat(boatName: BoatName, owner: UserId): IO[JoinedBoat] =
    sql"""insert into boats(name, owner) values($boatName, $owner)""".update
      .withUniqueGeneratedKeys[DeviceId]("id")
      .flatMap { id =>
        sql"${CommonSql.boats} and b.id = $id".query[JoinedBoat].unique
      }

  private def joinBoat(meta: BoatTrackMeta): IO[BoatRow] = {
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
              if (exists) {
                fail(
                  s"Boat name '${meta.boat}' is already taken and therefore not available for '${meta.user}'."
                )
              } else {
                saveNewBoat(meta.boat, uid, BoatTokens.random()).flatMap { id => boatById(id) }
              }
            }
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

  private def updateTrack(
    tid: ConnectionIO[TrackId],
    update: TrackId => ConnectionIO[Int]
  ): Future[JoinedTrack] = db.run {
    for {
      id <- tid
      _ <- update(id)
      updated <- trackById(id)
    } yield updated
  }
}