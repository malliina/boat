package com.malliina.boat.db

import java.time.{Instant, LocalDate}

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.{CreatedTimestampType, GetDummy, NumThreads, log}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.{Email, UserId, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.{GetResult, H2Profile, JdbcType, PositionedResult}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object BoatSchema {
  private val log = Logger(getClass)

  // Use this for all timestamps, otherwise MySQL applies an ON UPDATE CURRENT_TIMESTAMP clause by default
  val CreatedTimestampType = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL"
  val NumThreads = 20

  def apply(ds: DataSource, profile: ProfileConf): BoatSchema =
    new BoatSchema(ds, profile)

  def apply(conf: DatabaseConf): BoatSchema =
    apply(dataSource(conf), conf.profileConf)

  def dataSource(conf: DatabaseConf): HikariDataSource = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(conf.url)
    hikariConfig.setUsername(conf.user)
    hikariConfig.setPassword(conf.pass)
    hikariConfig.setDriverClassName(conf.driverName)
    log.info(s"Connecting to '${conf.url}'...")
    new HikariDataSource(hikariConfig)
  }

  def executor(threads: Int = NumThreads) = AsyncExecutor(
    name = "AsyncExecutor.boat",
    minThreads = threads,
    maxThreads = threads,
    queueSize = 20000,
    maxConnections = threads
  )

  object GetDummy extends GetResult[Int] {
    override def apply(v1: PositionedResult) = 0
  }

}

class BoatSchema(ds: DataSource, conf: ProfileConf)
    extends DatabaseLike(
      conf.profile,
      conf.profile.api.Database
        .forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))) {
  val api = new Mappings(impl) with impl.API

  import api._

  val usersTable = TableQuery[UsersTable]
  val boatsTable = TableQuery[BoatsTable]
  val tracksTable = TableQuery[TracksTable]
  val sentencesTable = TableQuery[SentencesTable]
  val pointsTable = TableQuery[TrackPointsTable]
  val sentencePointsTable = TableQuery[SentencesPointsLink]
  val pushTable = TableQuery[PushClientsTable]
  val fairwaysTable = TableQuery[FairwaysTable]
  val fairwayCoordsTable = TableQuery[FairwayCoordsTable]
  val pushInserts = pushTable.map(_.forInserts).returning(pushTable.map(_.id))
  val sentenceInserts = sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))
  val trackInserts = tracksTable.map(_.forInserts).returning(tracksTable.map(_.id))
  val coordInserts = pointsTable.map(_.forInserts).returning(pointsTable.map(_.id))

  // The H2 function is wrong, but I just want something that compiles for H2
  val distanceFunc = impl match {
    case H2Profile => "ST_MaxDistance"
    case _         => "ST_Distance_Sphere"
  }
  val dateFuncName = impl match {
    case H2Profile => "truncate"
    case _         => "date"
  }
  val distanceCoords = SimpleFunction.binary[Coord, Coord, DistanceM](distanceFunc)
  val topSpeeds = trackAggregate(_.map(_.boatSpeed).max)
  val topPoints = pointsTable
    .join(
      pointsTable
        .join(topSpeeds)
        .on((p, t) => p.track === t._1 && p.boatSpeed === t._2)
        .groupBy(_._1.track)
        .map { case (t, q) => (t, q.map(_._1.id).min) })
    .on(_.id === _._2)
  val minTimes = trackAggregate(_.map(_.boatTime).min)
  val maxTimes = trackAggregate(_.map(_.boatTime).max)
  val boatsView: Query[LiftedJoinedBoat, JoinedBoat, Seq] =
    boatsTable.join(usersTable).on(_.owner === _.id).map {
      case (b, u) => LiftedJoinedBoat(b.id, b.name, b.token, u.id, u.user, u.email, u.language)
    }
  val tracksViewNonEmpty: Query[LiftedJoinedTrack, JoinedTrack, Seq] =
    boatsView
      .join(tracksTable)
      .on(_.boat === _.boat)
      .join(topSpeeds)
      .on(_._2.id === _._1)
      .join(minTimes)
      .on(_._1._2.id === _._1)
      .join(maxTimes)
      .on(_._1._1._2.id === _._1)
      .join(topPoints)
      .on(_._1._1._1._2.id === _._1.track)
      .map {
        case (((((boat, track), (_, top)), (_, start)), (_, end)), (point, _)) =>
          LiftedJoinedTrack(
            track.id,
            track.name,
            track.title,
            track.canonical,
            track.comments,
            track.added,
            boat.boat,
            boat.boatName,
            boat.token,
            boat.user,
            boat.username,
            boat.email,
            boat.language,
            track.points,
            start,
            end,
            top,
            track.avgSpeed,
            track.avgWaterTemp,
            track.distance,
            point.combined
          )
      }
  val trackMetas: Query[LiftedTrackMeta, TrackMeta, Seq] =
    boatsView.join(tracksTable).on(_.boat === _.boat).map {
      case (b, t) =>
        LiftedTrackMeta(
          t.id,
          t.name,
          t.title,
          t.canonical,
          t.comments,
          t.added,
          t.avgSpeed,
          t.avgWaterTemp,
          t.points,
          t.distance,
          b.boat,
          b.boatName,
          b.token,
          b.user,
          b.username,
          b.email
        )
    }

  override val tableQueries = Seq(pushTable,
                                  sentencePointsTable,
                                  pointsTable,
                                  sentencesTable,
                                  tracksTable,
                                  boatsTable,
                                  usersTable,
                                  fairwayCoordsTable,
                                  fairwaysTable)

  def topSpeedPoint(track: TrackId) =
    pointsTable.filter(_.track === track).sortBy(_.boatSpeed.desc).take(1)

  def trackAggregate[N: JdbcType](
      agg: Query[TrackPointsTable, TrackPointRow, Seq] => Rep[Option[N]])
    : Query[(Rep[TrackId], Rep[Option[N]]), (TrackId, Option[N]), Seq] =
    pointsTable.groupBy(_.track).map { case (t, q) => (t, agg(q)) }

  def dateFunc: Rep[Instant] => Rep[LocalDate] =
    SimpleFunction.unary[Instant, LocalDate](dateFuncName)

  case class LiftedJoinedBoat(boat: Rep[BoatId],
                              boatName: Rep[BoatName],
                              token: Rep[BoatToken],
                              user: Rep[UserId],
                              username: Rep[Username],
                              email: Rep[Option[Email]],
                              language: Rep[Language])

  implicit object JoinedBoatShape extends CaseClassShape(LiftedJoinedBoat.tupled, JoinedBoat.tupled)

  case class LiftedTrackMeta(track: Rep[TrackId],
                             trackName: Rep[TrackName],
                             trackTitle: Rep[Option[TrackTitle]],
                             trackCanonical: Rep[TrackCanonical],
                             comments: Rep[Option[String]],
                             trackAdded: Rep[Instant],
                             avgSpeed: Rep[Option[SpeedM]],
                             avgWaterTemp: Rep[Option[Temperature]],
                             points: Rep[Int],
                             distance: Rep[DistanceM],
                             boat: Rep[BoatId],
                             boatName: Rep[BoatName],
                             token: Rep[BoatToken],
                             user: Rep[UserId],
                             username: Rep[Username],
                             email: Rep[Option[Email]])

  implicit object LiftedTrackMetaShape
      extends CaseClassShape(LiftedTrackMeta.tupled, (TrackMeta.apply _).tupled)

  case class LiftedCoord(id: Rep[TrackPointId],
                         lon: Rep[Longitude],
                         lat: Rep[Latitude],
                         coord: Rep[Coord],
                         boatSpeed: Rep[SpeedM],
                         waterTemp: Rep[Temperature],
                         depth: Rep[DistanceM],
                         depthOffset: Rep[DistanceM],
                         boatTime: Rep[Instant],
                         date: Rep[LocalDate],
                         track: Rep[TrackId],
                         added: Rep[Instant])

  implicit object Coordshape
      extends CaseClassShape(LiftedCoord.tupled, (CombinedCoord.apply _).tupled)

  case class LiftedJoinedTrack(track: Rep[TrackId],
                               trackName: Rep[TrackName],
                               trackTitle: Rep[Option[TrackTitle]],
                               canonical: Rep[TrackCanonical],
                               comments: Rep[Option[String]],
                               trackAdded: Rep[Instant],
                               boat: Rep[BoatId],
                               boatName: Rep[BoatName],
                               boatToken: Rep[BoatToken],
                               user: Rep[UserId],
                               username: Rep[Username],
                               email: Rep[Option[Email]],
                               language: Rep[Language],
                               points: Rep[Int],
                               start: Rep[Option[Instant]],
                               end: Rep[Option[Instant]],
                               topSpeed: Rep[Option[SpeedM]],
                               avgSpeed: Rep[Option[SpeedM]],
                               avgWaterTemp: Rep[Option[Temperature]],
                               length: Rep[DistanceM],
                               topPoint: LiftedCoord)

  implicit object TrackShape
      extends CaseClassShape(LiftedJoinedTrack.tupled, (JoinedTrack.apply _).tupled)

  case class LiftedTrackStats(track: Rep[TrackId],
                              start: Rep[Option[Instant]],
                              end: Rep[Option[Instant]],
                              topSpeed: Rep[Option[SpeedM]])

  implicit object TrackStatsShape
      extends CaseClassShape(LiftedTrackStats.tupled, (TrackNumbers.apply _).tupled)

  def initBoat()(implicit ec: ExecutionContext) = {
    if (conf.profile == H2Profile) {
      val clazz = "org.h2gis.functions.factory.H2GISFunctions.load"
      val a = for {
        _ <- sqlu"""CREATE ALIAS IF NOT EXISTS H2GIS_SPATIAL FOR "#$clazz";"""
        _ <- sql"CALL H2GIS_SPATIAL();".as[Int](GetDummy)
      } yield ()
      runAndAwait(a)
      log.info("Initialized H2GIS spatial extensions.")
    }
    init()
    val addAnon = usersTable.filter(_.user === Usernames.anon).exists.result.flatMap { exists =>
      if (exists) DBIO.successful(())
      else userInserts += NewUser(Usernames.anon, None, UserToken.random(), enabled = true)
    }
    await(run(addAnon))
  }

  class SentencesTable(tag: Tag) extends Table[SentenceRow](tag, "sentences") {
    def id = column[SentenceKey]("id", O.AutoInc, O.PrimaryKey)
    def sentence = column[RawSentence]("sentence", O.Length(128))
    def track = column[TrackId]("track")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("sentences_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (sentence, track) <> ((SentenceInput.apply _).tupled, SentenceInput.unapply)

    def * = (id, sentence, track, added) <> ((SentenceRow.apply _).tupled, SentenceRow.unapply)
  }

  class TrackPointsTable(tag: Tag) extends Table[TrackPointRow](tag, "points") {
    def id = column[TrackPointId]("id", O.PrimaryKey, O.AutoInc)
    def lon = column[Longitude]("longitude")
    def lat = column[Latitude]("latitude")
    def coord = column[Coord]("coord")
    def boatSpeed = column[SpeedM]("boat_speed")
    def speedIdx = index("points_track_speed_idx", (track, boatSpeed))
    def waterTemp = column[Temperature]("water_temp")
    def tempIdx = index("points_track_water_temp_idx", (track, waterTemp))
    def depth = column[DistanceM]("depthm")
    def depthIdx = index("points_track_depth_idx", (track, depth))
    def depthOffset = column[DistanceM]("depth_offsetm")
    def boatTime = column[Instant]("boat_time", O.SqlType(CreatedTimestampType))
    def timeIdx = index("points_track_boat_time_idx", (track, boatTime))
    def track = column[TrackId]("track")
    def trackIndex = column[Int]("track_index", O.Default(0))
    def trackIndexIdx = index("points_track_index_idx", trackIndex, unique = false)
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))
    def diff = column[DistanceM]("diff", O.Default(DistanceM.zero))

    def trackConstraint = foreignKey("points2_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def diffIdx = index("points_track_diff_idx", (track, diff))
    // I think this is the best index for aggregate calculations (group by with avg, sum, max, min)
    def allIdx = index("points_track_all_idx", (track, boatTime, boatSpeed, waterTemp, diff))

    def forInserts = (lon,
                      lat,
                      coord,
                      boatSpeed,
                      waterTemp,
                      depth,
                      depthOffset,
                      boatTime,
                      track,
                      trackIndex,
                      diff) <> ((TrackPointInput.apply _).tupled, TrackPointInput.unapply)
    def combined = LiftedCoord(id,
                               lon,
                               lat,
                               coord,
                               boatSpeed,
                               waterTemp,
                               depth,
                               depthOffset,
                               boatTime,
                               dateFunc(boatTime),
                               track,
                               added)
    def * = (id,
             lon,
             lat,
             coord,
             boatSpeed,
             waterTemp,
             depth,
             depthOffset,
             boatTime,
             track,
             trackIndex,
             diff,
             added) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
  }

  class SentencesPointsLink(tag: Tag) extends Table[SentencePointLink](tag, "sentence_points") {
    def sentence = column[SentenceKey]("sentence")
    def point = column[TrackPointId]("point")

    def pKey = primaryKey("sentence_points_pk", (sentence, point))

    def sentenceConstraint = foreignKey("sentence_points_sentence_fk", sentence, sentencesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def pointConstraint = foreignKey("sentence_points_point_fk", point, pointsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def * = (sentence, point) <> ((SentencePointLink.apply _).tupled, SentencePointLink.unapply)
  }

  class TracksTable(tag: Tag) extends Table[TrackRow](tag, "tracks") {
    def id = column[TrackId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[TrackName]("name", O.Length(128))
    def boat = column[BoatId]("boat")
    def avgSpeed = column[Option[SpeedM]]("avg_speed")
    def avgWaterTemp = column[Option[Temperature]]("avg_water_temp")
    def points = column[Int]("points")
    def distance = column[DistanceM]("distance")
    def title = column[Option[TrackTitle]]("title", O.Length(191), O.Unique)
    def canonical = column[TrackCanonical]("canonical", O.Length(191), O.Unique)
    def comments = column[Option[String]]("comments")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def boatConstraint = foreignKey("tracks_boat_fk", boat, boatsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts =
      (name, boat, avgSpeed, avgWaterTemp, points, distance, canonical) <> ((TrackInput.apply _).tupled, TrackInput.unapply)

    def * = (id,
             name,
             boat,
             avgSpeed,
             avgWaterTemp,
             points,
             distance,
             title,
             canonical,
             comments,
             added) <> ((TrackRow.apply _).tupled, TrackRow.unapply)
  }

  class BoatsTable(tag: Tag) extends Table[BoatRow](tag, "boats") {
    def id = column[BoatId]("id", O.PrimaryKey, O.AutoInc)
    def name = column[BoatName]("name", O.Unique, O.Length(128))
    def token = column[BoatToken]("token", O.Unique, O.Length(128))
    def owner = column[UserId]("owner")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("boats_owner_fk", owner, usersTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (name, token, owner) <> ((BoatInput.apply _).tupled, BoatInput.unapply)

    def * = (id, name, token, owner, added) <> ((BoatRow.apply _).tupled, BoatRow.unapply)
  }

  class UsersTable(tag: Tag) extends Table[DataUser](tag, "users") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)
    def user = column[Username]("user", O.Unique, O.Length(128))
    def email = column[Option[Email]]("email", O.Unique, O.Length(128))
    def passHash = column[String]("pass_hash", O.Length(512))
    def token = column[UserToken]("token", O.Length(128), O.Unique)
    def language = column[Language]("language", O.Length(64), O.Default(Language.default))
    def enabled = column[Boolean]("enabled")
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (user, email, token, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * =
      (id, user, email, token, language, enabled, added) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

  class PushClientsTable(tag: Tag) extends Table[PushDevice](tag, "push_clients") {
    def id = column[PushId]("id", O.PrimaryKey, O.AutoInc)
    def token = column[PushToken]("token", O.Unique, O.Length(1024))
    def device = column[MobileDevice]("device", O.Length(128))
    def user = column[UserId]("user", O.Length(128))
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (token, device, user).mapTo[PushInput]
    def * = (id, token, device, user, added) <> ((PushDevice.apply _).tupled, PushDevice.unapply)
  }

  class FairwaysTable(tag: Tag) extends Table[FairwayRow](tag, "fairways") {
    def id = column[FairwayId]("id", O.PrimaryKey, O.AutoInc)
    def nameFi = column[Option[String]]("name_fi", O.Length(128))
    def nameSe = column[Option[String]]("name_se", O.Length(128))
    def start = column[Option[String]]("start", O.Length(128))
    def end = column[Option[String]]("end", O.Length(128))
    def depth = column[Option[DistanceM]]("depth")
    def depth2 = column[Option[DistanceM]]("depth2")
    def depth3 = column[Option[DistanceM]]("depth3")
    def lighting = column[FairwayLighting]("lighting")
    def classText = column[String]("class_text")
    def seaArea = column[SeaArea]("sea_area")
    def state = column[Double]("state")

    def forInserts = (nameFi,
                      nameSe,
                      start,
                      end,
                      depth,
                      depth2,
                      depth3,
                      lighting,
                      classText,
                      seaArea,
                      state) <> ((FairwayInfo.apply _).tupled, FairwayInfo.unapply)
    def * = (id,
             nameFi,
             nameSe,
             start,
             end,
             depth,
             depth2,
             depth3,
             lighting,
             classText,
             seaArea,
             state) <> ((FairwayRow.apply _).tupled, FairwayRow.unapply)
  }

  class FairwayCoordsTable(tag: Tag) extends Table[FairwayCoord](tag, "fairway_coords") {
    def id = column[FairwayCoordId]("id", O.PrimaryKey, O.AutoInc)
    def coord = column[Coord]("coord")
    def lat = column[Latitude]("latitude")
    def lng = column[Longitude]("longitude")
    def hash = column[CoordHash]("coord_hash", O.Length(191))
    def fairway = column[FairwayId]("fairway")

    def fairwayConstraint = foreignKey("fairway_coords_fairway_fk", fairway, fairwaysTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def hashIdx = index("fairway_coords_coord_hash_idx", hash)

    def forInserts =
      (coord, lat, lng, hash, fairway) <> ((FairwayCoordInput.apply _).tupled, FairwayCoordInput.unapply)
    def * =
      (id, coord, lat, lng, hash, fairway) <> ((FairwayCoord.apply _).tupled, FairwayCoord.unapply)
  }

  def first[T, R](q: Query[T, R, Seq], onNotFound: => String)(implicit ec: ExecutionContext) =
    q.result.headOption.flatMap { maybeRow =>
      maybeRow.map(DBIO.successful).getOrElse {
        log.warn(onNotFound)
        DBIO.failed(new NotFoundException(onNotFound))
      }
    }
}
