package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.{CreatedTimestampType, NumThreads}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import controllers.BoatController
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.JdbcProfile
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object BoatSchema {
  private val log = Logger(getClass)

  val CreatedTimestampType = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL"
  val NumThreads = 20

  def apply(profile: JdbcProfile, ds: DataSource): BoatSchema =
    new BoatSchema(ds, profile)

  def apply(conf: DatabaseConf): BoatSchema = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(conf.url)
    hikariConfig.setUsername(conf.user)
    hikariConfig.setPassword(conf.pass)
    hikariConfig.setDriverClassName(conf.driverName)
    log.info(s"Connecting to '${conf.url}'...")
    apply(conf.profile, new HikariDataSource(hikariConfig))
  }

  def executor(threads: Int = NumThreads) = AsyncExecutor(
    name = "AsyncExecutor.boat",
    minThreads = threads,
    maxThreads = threads,
    queueSize = 20000,
    maxConnections = threads
  )
}

class BoatSchema(ds: DataSource, override val impl: JdbcProfile)
  extends DatabaseLike(impl, impl.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))) {

  val api = impl.api

  import api._

  object mappings extends Mappings(impl)

  import mappings._

  val usersTable = TableQuery[UsersTable]
  val boatsTable = TableQuery[BoatsTable]
  val tracksTable = TableQuery[TracksTable]
  val sentencesTable = TableQuery[SentencesTable]
  val coordsTable = TableQuery[TrackPointsTable]
  val sentenceInserts = sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))
  val trackInserts = tracksTable.map(_.forInserts).returning(tracksTable.map(_.id))
  val coordInserts = coordsTable.map(_.forInserts).returning(coordsTable.map(_.id))

  override val tableQueries = Seq(coordsTable, sentencesTable, tracksTable, boatsTable, usersTable)

  case class JoinedBoat(boat: BoatId, boatName: BoatName, boatToken: BoatToken,
                        user: UserId, username: User, email: Option[UserEmail])

  case class LiftedJoinedBoat(boat: Rep[BoatId], boatName: Rep[BoatName], token: Rep[BoatToken],
                              user: Rep[UserId], username: Rep[User], email: Rep[Option[UserEmail]])

  implicit object JoinedBoatShape extends CaseClassShape(LiftedJoinedBoat.tupled, JoinedBoat.tupled)

  val boatsView: Query[LiftedJoinedBoat, JoinedBoat, Seq] = boatsTable.join(usersTable).on(_.owner === _.id)
    .map { case (b, u) => LiftedJoinedBoat(b.id, b.name, b.token, u.id, u.user, u.email) }

  case class LiftedJoinedTrack(track: Rep[TrackId], trackName: Rep[TrackName], trackAdded: Rep[Instant],
                               boat: Rep[BoatId], boatName: Rep[BoatName], boatToken: Rep[BoatToken],
                               user: Rep[UserId], username: Rep[User], email: Rep[Option[UserEmail]],
                               points: Rep[Int], start: Rep[Option[Instant]], end: Rep[Option[Instant]])

  implicit object TrackShape extends CaseClassShape(LiftedJoinedTrack.tupled, (JoinedTrack.apply _).tupled)

  val tracksViewNonEmpty: Query[LiftedJoinedTrack, JoinedTrack, Seq] =
    coordsTable.join(tracksTable).on(_.track === _.id).join(boatsView).on(_._2.boat === _.boat)
      .groupBy { case ((_, ts), bs) => (bs, ts) }
      .map { case ((bs, ts), q) => LiftedJoinedTrack(
        ts.id, ts.name, ts.added, bs.boat, bs.boatName, bs.token, bs.user,
        bs.username, bs.email, q.length, q.map(_._1._1.added).min, q.map(_._1._1.added).max)
      }

  val tracksView: Query[LiftedJoinedTrack, JoinedTrack, Seq] =
    boatsView.join(tracksTable).on(_.boat === _.boat).joinLeft(coordsTable).on(_._2.id === _.track)
        .groupBy { case ((bs, ts), _) => (bs, ts) }
        .map { case ((bs, ts), q) => LiftedJoinedTrack(
          ts.id, ts.name, ts.added, bs.boat, bs.boatName, bs.token, bs.user,
          bs.username, bs.email, q.length, q.map(_._2.map(_.added)).min, q.map(_._2.map(_.added)).max) }

  def first[T, R](q: Query[T, R, Seq], onNotFound: => String)(implicit ec: ExecutionContext) =
    q.result.headOption.flatMap { maybeRow =>
      maybeRow.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(onNotFound)))
    }

  def initBoat()(implicit ec: ExecutionContext) = {
    init()
    val addAnon = usersTable.filter(_.user === User.anon).exists.result.flatMap { exists =>
      if (exists) DBIO.successful(())
      else userInserts += NewUser(User.anon, None, "unused", enabled = true)
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

  class TrackPointsTable(tag: Tag) extends Table[TrackPointRow](tag, "points") {
    def id = column[TrackPointId]("id", O.PrimaryKey, O.AutoInc)

    def lon = column[Double]("longitude")

    def lat = column[Double]("latitude")

    def boatTime = column[Instant]("boat_time")

    def track = column[TrackId]("track")

    def trackConstraint = foreignKey("points_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (lon, lat, boatTime, track) <> ((TrackPointInput.apply _).tupled, TrackPointInput.unapply)

    def * = (id, lon, lat, boatTime, track, added) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
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

    def user = column[User]("user", O.Unique, O.Length(128))

    def email = column[Option[UserEmail]]("email", O.Unique, O.Length(128))

    def passHash = column[String]("pass_hash", O.Length(512))

    def enabled = column[Boolean]("enabled")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (user, email, passHash, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * = (id, user, email, passHash, enabled, added) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

}
