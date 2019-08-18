package com.malliina.boat.db

import java.time.{Instant, ZoneId}

import com.malliina.boat._
import com.malliina.boat.db.BoatSchema.{GetDummy, NumThreads, log}
import com.malliina.measure.DistanceM
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import play.api.Logger
import slick.jdbc.{GetResult, JdbcType, PositionedResult}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object BoatSchema {
  private val log = Logger(getClass)

  // Use this for all timestamps, otherwise MySQL applies an ON UPDATE CURRENT_TIMESTAMP clause by default
  val CreatedTimestampType = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL"
  val NumThreads = 20

  val helsinkiZone = ZoneId.of("Europe/Helsinki")

  def apply(ds: DataSource, profile: BoatJdbcProfile): BoatSchema =
    new BoatSchema(ds, profile)

  def apply(conf: DatabaseConf): BoatSchema =
    apply(dataSource(conf), conf.profile)

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
    override def apply(v1: PositionedResult): Int = 0
  }

}

class BoatSchema(ds: DataSource, val jdbc: BoatJdbcProfile)
    extends DatabaseClient
    with JdbcComponent
    with Mappings
    with TracksSchema
    with FairwaySchema
    with PushSchema
    with QueryModels {
  import jdbc.api._

  val database: jdbc.backend.DatabaseDef =
    jdbc.api.Database.forDataSource(ds, Option(NumThreads), BoatSchema.executor(NumThreads))

  val SECOND = SimpleLiteral[String]("SECOND")
  val timestampDiff =
    SimpleFunction.ternary[String, Instant, Instant, FiniteDuration]("TIMESTAMPDIFF")
  def secondsDiff(from: Rep[Instant], to: Rep[Instant]) = timestampDiff(SECOND, from, to)
  val monthOf = SimpleFunction.unary[Instant, MonthVal]("MONTH")
  val yearOf = SimpleFunction.unary[Instant, YearVal]("YEAR")

  val topSpeeds = trackAggregate(_.map(_.boatSpeed).max)
  val topPoints = pointsTable
    .join(
      pointsTable
        .join(topSpeeds)
        .on((p, t) => p.track === t._1 && p.boatSpeed === t._2)
        .groupBy(_._1.track)
        .map { case (t, q) => (t, q.map(_._1.id).min) })
    .on(_.id === _._2)
  val minTimes: Query[(Rep[TrackId], Rep[Option[Instant]]), (TrackId, Option[Instant]), Seq] =
    trackAggregate(_.map(_.boatTime).min)
  val maxTimes: Query[(Rep[TrackId], Rep[Option[Instant]]), (TrackId, Option[Instant]), Seq] =
    trackAggregate(_.map(_.boatTime).max)
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
        case (((((boat, track), (_, top)), (_, start: Rep[Option[Instant]])), (_, end)),
              (point, _)) =>
          val startOrNow = start.getOrElse(Instant.now().bind)
          val endOrNow = end.getOrElse(Instant.now().bind)

          LiftedJoinedTrack(
            track.id,
            track.name,
            track.title,
            track.canonical,
            track.comments,
            track.added,
            LiftedJoinedBoat(
              boat.boat,
              boat.boatName,
              boat.token,
              boat.user,
              boat.username,
              boat.email,
              boat.language
            ),
            track.points,
            start,
            dateOf(startOrNow),
            monthOf(startOrNow),
            yearOf(startOrNow),
            end,
            secondsDiff(startOrNow, endOrNow),
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

  val tableQueries = Seq(
    gpsSentencePointsTable,
    gpsPointsTable,
    gpsSentencesTable,
    pushTable,
    sentencePointsTable,
    pointsTable,
    sentencesTable,
    tracksTable,
    boatsTable,
    usersTable,
    fairwayCoordsTable,
    fairwaysTable
  )

  def trackAggregate[N: JdbcType](
      agg: Query[TrackPointsTable, TrackPointRow, Seq] => Rep[Option[N]])
    : Query[(Rep[TrackId], Rep[Option[N]]), (TrackId, Option[N]), Seq] =
    pointsTable.groupBy(_.track).map { case (t, q) => (t, agg(q)) }

  def initApp()(implicit ec: ExecutionContext) = {
    if (jdbc == InstantH2Profile) {
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

  def init(): Unit = {
    log info s"Ensuring all tables exist..."
    createIfNotExists(tableQueries: _*)
  }
}
