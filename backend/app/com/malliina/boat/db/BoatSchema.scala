package com.malliina.boat.db

import java.time.{Instant, LocalDate}

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.{CreatedTimestampType, GetDummy, log, NumThreads}
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values.{Email, UserId, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.{GetResult, H2Profile, PositionedResult}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object BoatSchema {
  private val log = Logger(getClass)

  // Use this for all timestamps, otherwise MySQL applies an ON UPDATE CURRENT_TIMESTAMP clause by default
  val CreatedTimestampType = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL"
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
  extends DatabaseLike(conf.profile, conf.profile.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))) {
  val api = new Mappings(impl) with impl.API

  import api._

  val usersTable = TableQuery[UsersTable]
  val boatsTable = TableQuery[BoatsTable]
  val tracksTable = TableQuery[TracksTable]
  val sentencesTable = TableQuery[SentencesTable]
  val pointsTable = TableQuery[TrackPointsTable]
  val sentencePointsTable = TableQuery[SentencesPointsLink]
  val sentenceInserts = sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))
  val trackInserts = tracksTable.map(_.forInserts).returning(tracksTable.map(_.id))
  val coordInserts = pointsTable.map(_.forInserts).returning(pointsTable.map(_.id))

  // The H2 function is wrong, but I just want something that compiles for H2
  val distanceFunc = impl match {
    case H2Profile => "ST_MaxDistance"
    case _ => "ST_Distance_Sphere"
  }
  private val distanceCoords = SimpleFunction.binary[Coord, Coord, Double](distanceFunc)
  val distances = pointsTable
    .join(pointsTable).on((c1, c2) => c1.track === c2.track && c1.id === c2.previous)
    .map { case (c1, c2) => (c1.track, distanceCoords(c1.coord, c2.coord)) }
    .groupBy(_._1).map { case (track, q) => (track, q.map(_._2).sum.map(_.asColumnOf[Distance](distanceMappingMeters))) }

  def dateFunc: Rep[Instant] => Rep[LocalDate] =
    SimpleFunction.unary[Instant, LocalDate]("date")

  override val tableQueries = Seq(sentencePointsTable, pointsTable, sentencesTable, tracksTable, boatsTable, usersTable)

  case class JoinedBoat(boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                        user: UserId, username: Username, email: Option[Email])

  case class LiftedJoinedBoat(boat: Rep[BoatId], boatName: Rep[BoatName], token: Rep[BoatToken],
                              user: Rep[UserId], username: Rep[Username], email: Rep[Option[Email]])

  implicit object JoinedBoatShape extends CaseClassShape(LiftedJoinedBoat.tupled, JoinedBoat.tupled)

  val boatsView: Query[LiftedJoinedBoat, JoinedBoat, Seq] = boatsTable.join(usersTable).on(_.owner === _.id)
    .map { case (b, u) => LiftedJoinedBoat(b.id, b.name, b.token, u.id, u.user, u.email) }

  case class LiftedJoinedTrack(track: Rep[TrackId], trackName: Rep[TrackName], trackAdded: Rep[Instant],
                               boat: Rep[BoatId], boatName: Rep[BoatName], boatToken: Rep[BoatToken],
                               user: Rep[UserId], username: Rep[Username], email: Rep[Option[Email]],
                               points: Rep[Int], start: Rep[Option[Instant]], end: Rep[Option[Instant]],
                               topSpeed: Rep[Option[Speed]], avgSpeed: Rep[Option[Speed]],
                               avgWaterTemp: Rep[Option[Temperature]], length: Rep[Option[Distance]])

  implicit object TrackShape extends CaseClassShape(LiftedJoinedTrack.tupled, (JoinedTrack.apply _).tupled)

  case class LiftedCoord(id: Rep[TrackPointId], lon: Rep[Double], lat: Rep[Double], coord: Rep[Coord],
                         boatSpeed: Rep[Speed], waterTemp: Rep[Temperature], depth: Rep[Distance],
                         depthOffset: Rep[Distance], boatTime: Rep[Instant], date: Rep[LocalDate],
                         track: Rep[TrackId], added: Rep[Instant])

  implicit object Coordshape extends CaseClassShape(LiftedCoord.tupled, (CombinedCoord.apply _).tupled)

  private val minSpeed: Speed = Speed.kmh(1)

  val tracksViewNonEmpty: Query[LiftedJoinedTrack, JoinedTrack, Seq] =
    pointsTable.join(tracksTable).on(_.track === _.id).join(boatsView).on(_._2.boat === _.boat).join(distances).on(_._1._2.id === _._1)
      .groupBy { case (((_, ts), bs), _) => (bs, ts) }
      .map { case ((bs, ts), q) =>
        // TODO should filter average speed by minSpeed, but Slick throws an exception. However, works with trackView.
        LiftedJoinedTrack(
          ts.id, ts.name, ts.added, bs.boat, bs.boatName, bs.token, bs.user,
          bs.username, bs.email, q.length,
          q.map(_._1._1._1.boatTime).min, q.map(_._1._1._1.boatTime).max,
          q.map(_._1._1._1.boatSpeed).max, q.map(_._1._1._1.boatSpeed).avg,
          q.map(_._1._1._1.waterTemp).max, q.map(_._2._2).max
        )
      }

  // TODO Fix - do not join points
  val tracksView: Query[LiftedJoinedTrack, JoinedTrack, Seq] =
    boatsView.join(tracksTable).on(_.boat === _.boat).joinLeft(pointsTable).on(_._2.id === _.track)
      .groupBy { case ((bs, ts), _) => (bs, ts) }
      .map { case ((bs, ts), q) => LiftedJoinedTrack(
        ts.id, ts.name, ts.added, bs.boat, bs.boatName, bs.token, bs.user,
        bs.username, bs.email, q.length,
        q.map(_._2.map(_.boatTime)).min, q.map(_._2.map(_.boatTime)).max,
        q.map(_._2.map(_.boatSpeed)).max, q.map(_._2.map(_.boatSpeed).filter(_ >= minSpeed)).avg,
        q.map(_._2.map(_.waterTemp)).avg, Distance.zero.bind.?
      )
      }

  def first[T, R](q: Query[T, R, Seq], onNotFound: => String)(implicit ec: ExecutionContext) =
    q.result.headOption.flatMap { maybeRow =>
      maybeRow.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(onNotFound)))
    }

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
      else userInserts += NewUser(Usernames.anon, None, "unused", UserToken.random(), enabled = true)
    }
    await(run(addAnon))
  }

  class SentencesTable(tag: Tag) extends Table[SentenceRow](tag, "sentences2") {
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

  class TrackPointsTable(tag: Tag) extends Table[TrackPointRow](tag, "points2") {
    def id = column[TrackPointId]("id", O.PrimaryKey, O.AutoInc)

    def lon = column[Double]("longitude")

    def lat = column[Double]("latitude")

    def coord = column[Coord]("coord")

    def boatSpeed = column[Speed]("boat_speed")

    def waterTemp = column[Temperature]("water_temp")

    def depth = column[Distance]("depthm")

    def depthOffset = column[Distance]("depth_offsetm")

    def boatTime = column[Instant]("boat_time", O.SqlType(CreatedTimestampType))

    def track = column[TrackId]("track")

    def trackIndex = column[Int]("track_index", O.Default(0))

    def trackIndexIdx = index("points_track_index_idx", trackIndex, unique = false)

    def trackConstraint = foreignKey("points2_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def previous = column[Option[TrackPointId]]("previous")

    def previousConstraint = foreignKey("points_previous_fk", previous, pointsTable)(
      _.id.?,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (lon, lat, coord, boatSpeed, waterTemp, depth, depthOffset, boatTime, track, trackIndex, previous) <> ((TrackPointInput.apply _).tupled, TrackPointInput.unapply)

    def combined = LiftedCoord(id, lon, lat, coord, boatSpeed, waterTemp, depth, depthOffset, boatTime, dateFunc(boatTime), track, added)

    def * = (id, lon, lat, coord, boatSpeed, waterTemp, depth, depthOffset, boatTime, track, trackIndex, previous, added) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
  }

  class SentencesPointsLink(tag: Tag) extends Table[SentencePointLink](tag, "sentence_points") {
    def sentence = column[SentenceKey]("sentence")

    def point = column[TrackPointId]("point")

    def pKey = primaryKey("sentence_points_pk", (sentence, point))

    def sentenceConstraint = foreignKey("sentence_points_sentence_fk", sentence, sentencesTable)(_.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def pointConstraint = foreignKey("sentence_points_point_fk", point, pointsTable)(_.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade)

    def * = (sentence, point) <> ((SentencePointLink.apply _).tupled, SentencePointLink.unapply)
  }

  class TracksTable(tag: Tag) extends Table[TrackRow](tag, "tracks") {
    def id = column[TrackId]("id", O.PrimaryKey, O.AutoInc)

    def name = column[TrackName]("name", O.Length(128))

    def boat = column[BoatId]("boat")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def boatConstraint = foreignKey("tracks_boat_fk", boat, boatsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (name, boat) <> ((TrackInput.apply _).tupled, TrackInput.unapply)

    def * = (id, name, boat, added) <> ((TrackRow.apply _).tupled, TrackRow.unapply)
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

    def enabled = column[Boolean]("enabled")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (user, email, passHash, token, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * = (id, user, email, passHash, token, enabled, added) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

}
