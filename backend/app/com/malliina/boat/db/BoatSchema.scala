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
    queueSize = 10000,
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
  val sentenceInserts = sentencesTable.map(_.forInserts).returning(sentencesTable.map(_.id))
  val boatInserts = boatsTable.map(_.forInserts).returning(boatsTable.map(_.id))
  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))

  override val tableQueries = Seq(sentencesTable, tracksTable, boatsTable, usersTable)

  def initBoat()(implicit ec: ExecutionContext) = {
    init()
    val addAnon = usersTable.filter(_.user === BoatController.anonUser).exists.result.flatMap { exists =>
      if (exists) DBIO.successful(())
      else userInserts += NewUser(BoatController.anonUser, "unused", enabled = true)
    }
    await(run(addAnon))
  }

  class SentencesTable(tag: Tag) extends Table[SentenceRow](tag, "sentences") {
    def id = column[SentenceKey]("id", O.AutoInc, O.PrimaryKey)

    def sentence = column[RawSentence]("sentence")

    def boat = column[BoatId]("boat")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("sentences_boat_fk", boat, boatsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (sentence, boat) <> ((SentenceInput.apply _).tupled, SentenceInput.unapply)

    def * = (id, sentence, boat, added) <> ((SentenceRow.apply _).tupled, SentenceRow.unapply)
  }

  class TrackPointsTable(tag: Tag) extends Table[TrackPointRow](tag, "points") {
    def id = column[TrackPointId]("id", O.PrimaryKey, O.AutoInc)

    def lon = column[Double]("longitude")

    def lat = column[Double]("latitude")

    def track = column[TrackId]("track")

    def trackConstraint = foreignKey("points_track_fk", track, tracksTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def * = (id, lon, lat, track, added) <> ((TrackPointRow.apply _).tupled, TrackPointRow.unapply)
  }

  class TracksTable(tag: Tag) extends Table[TrackRow](tag, "tracks") {
    def id = column[TrackId]("id", O.PrimaryKey, O.AutoInc)

    def name = column[TrackName]("name", O.Unique, O.Length(128))

    def boat = column[BoatId]("boat")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def boatConstraint = foreignKey("tracks_boat_fk", boat, boatsTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def * = (id, name, boat, added) <> ((TrackRow.apply _).tupled, TrackRow.unapply)
  }

  class BoatsTable(tag: Tag) extends Table[BoatRow](tag, "boats") {
    def id = column[BoatId]("id", O.PrimaryKey, O.AutoInc)

    def name = column[BoatName]("name", O.Unique, O.Length(128))

    def owner = column[UserId]("owner")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def userConstraint = foreignKey("boats_owner_fk", owner, usersTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

    def forInserts = (name, owner) <> ((BoatInput.apply _).tupled, BoatInput.unapply)

    def * = (id, name, owner, added) <> ((BoatRow.apply _).tupled, BoatRow.unapply)
  }

  class UsersTable(tag: Tag) extends Table[DataUser](tag, "users") {
    def id = column[UserId]("id", O.PrimaryKey, O.AutoInc)

    def user = column[User]("user", O.Unique, O.Length(128))

    def passHash = column[String]("pass_hash", O.Length(512))

    def enabled = column[Boolean]("enabled")

    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (user, passHash, enabled) <> ((NewUser.apply _).tupled, NewUser.unapply)

    def * = (id, user, passHash, enabled, added) <> ((DataUser.apply _).tupled, DataUser.unapply)
  }

}
