package com.malliina.boat.db

import akka.actor.ActorSystem
import com.malliina.boat.db.NewTracksDatabase.log
import com.malliina.boat.http.{BoatQuery, TrackQuery}
import com.malliina.boat.parsing.FullCoord
import com.malliina.boat.{
  BoatName,
  BoatTrackMeta,
  CoordsEvent,
  DeviceId,
  DeviceMeta,
  FullTrack,
  InsertedPoint,
  JoinedBoat,
  JoinedTrack,
  KeyedSentence,
  Lang,
  Language,
  MinimalUserInfo,
  SentencesEvent,
  StatsResponse,
  TrackCanonical,
  TrackId,
  TrackInfo,
  TrackMeta,
  TrackName,
  TrackRef,
  TrackTitle,
  Tracks,
  TracksBundle
}
import com.malliina.values.{UserId, Username}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill._
import org.flywaydb.core.Flyway
import play.api.Logger
import NewTracksDatabase.fail
import com.malliina.boat.db.TracksDatabase.log

import scala.concurrent.{ExecutionContext, Future}

object NewTracksDatabase {
  private val log = Logger(getClass)

  def apply(as: ActorSystem, dbConf: Conf): NewTracksDatabase = {
    val pool = as.dispatchers.lookup("contexts.database")
    apply(dataSource(dbConf), pool)
  }

  def apply(ds: HikariDataSource, ec: ExecutionContext): NewTracksDatabase = ???
//    new NewTracksDatabase(ds)(ec)

  def mysqlFromEnvOrFail(as: ActorSystem) =
    withMigrations(as, Conf.fromEnvOrFail())

  def withMigrations(as: ActorSystem, conf: Conf) = {
    val flyway =
      Flyway.configure.dataSource(conf.url, conf.user, conf.pass).load()
    flyway.migrate()
    apply(as, conf)
  }

  def dataSource(conf: Conf): HikariDataSource = {
    val hikari = new HikariConfig()
    hikari.setDriverClassName(Conf.MySQLDriver)
    hikari.setJdbcUrl(conf.url)
    hikari.setUsername(conf.user)
    hikari.setPassword(conf.pass)
    log info s"Connecting to '${conf.url}'..."
    new HikariDataSource(hikari)
  }

  def fail(message: String): Nothing = throw new Exception(message)
}

abstract class NewTracksDatabase(val ds: HikariDataSource)(
  implicit val ec: ExecutionContext
) extends TracksSource {
  val naming = NamingStrategy(SnakeCase, MysqlEscape)
  lazy val ctx = new MysqlJdbcContext(naming, ds)
  import ctx._
  val boats = quote(querySchema[BoatRow]("boats"))
  val users = quote(querySchema[UserRow]("users"))
  val tracks = quote(querySchema[TrackRow]("tracks"))
  val boatsView = quote(boats.join(users).on(_.owner == _.id).map {
    case (b, u) =>
      JoinedBoat(b.id, b.name, b.token, u.id, u.user, u.email, u.language)
  })
  val tracksViewNonEmpty = quote(boatsView.join(tracks).on(_.device == _.boat))

  import NewMappings._

  def stats(user: MinimalUserInfo,
            limits: TrackQuery,
            lang: Lang): Future[StatsResponse]
  def updateTitle(track: TrackName,
                  title: TrackTitle,
                  user: UserId): Future[JoinedTrack]
  def updateComments(track: TrackId,
                     comments: String,
                     user: UserId): Future[JoinedTrack]
  def addBoat(boat: BoatName, user: UserId): Future[BoatRow]
  def removeDevice(device: DeviceId, user: UserId): Future[Int] = Future {
    val rows = run {
      boats.filter(b => b.owner == lift(user) && b.id == lift(device)).delete
    }
    if (rows == 1) {
      log.info(s"Deleted boat '$device' owned by '$user'.")
    } else {
      log.info(s"Boat '$device' owned by '$user' not found.")
    }
    rows.toInt
  }

  def renameBoat(boat: DeviceId,
                 newName: BoatName,
                 user: UserId): Future[BoatRow] = Future {
    val boatFilter = quote(
      boatsView.filter(b => b.userId == lift(user) && b.device == lift(boat))
    )
    transaction {
      val isEmpty = run(boatFilter.isEmpty)
      if (isEmpty)
        fail(s"Boat '$boat' by '$user' not found.")
      run(boats.filter(_.id == lift(boat)).update(_.name -> lift(newName)))
      val updated = run(boats.filter(_.id == lift(boat))).headOption
        .getOrElse(fail(s"Boat not found: '$boat'."))
      log.info(s"Renamed boat '$boat' to '$newName'.")
      updated
    }
  }

  /** If the given track and boat exist and are owned by the user, returns the track info.
    *
    * If the boat exists and is owned by the user but no track with the given name exists, the track is created.
    *
    * If neither the track nor boat exist, they are created.
    *
    * If the track name or boat name is already taken by another user, the returned Future fails.
    *
    * @param meta track, boat and user info
    * @return track specs, or failure if there is a naming clash
    */
  def joinAsBoat(meta: BoatTrackMeta): Future[TrackMeta]
  def joinAsDevice(meta: DeviceMeta): Future[JoinedBoat]
  def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]]
  def saveCoords(coords: FullCoord): Future[InsertedPoint]
  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): Future[Tracks]
  def tracksBundle(user: MinimalUserInfo,
                   filter: TrackQuery,
                   lang: Lang): Future[TracksBundle]
  def ref(track: TrackName, language: Language): Future[TrackRef]
  def canonical(track: TrackCanonical, language: Language): Future[TrackRef]
  def track(track: TrackName,
            user: Username,
            query: TrackQuery): Future[TrackInfo]
  def full(track: TrackName,
           language: Language,
           query: TrackQuery): Future[FullTrack]
  def history(user: MinimalUserInfo,
              limits: BoatQuery): Future[Seq[CoordsEvent]]
}
