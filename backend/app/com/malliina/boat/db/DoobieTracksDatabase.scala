package com.malliina.boat.db

import java.time.Instant

import cats.effect.IO._
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.malliina.boat.http.{Limits, SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.{CombinedCoord, DeviceId, JoinedBoat, JoinedTrack, TrackCanonical, TrackId, TrackName, TrackPointId, TrackPointRow, TrackTitle}
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.values.Username
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.log.{ExecFailure, ProcessingFailure, Success}
import doobie.util.transactor.Transactor.Aux
import javax.sql.DataSource
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

case class DoobieTrack(
  track: TrackId,
  trackName: TrackName,
  trackTitle: Option[TrackTitle],
  canonical: TrackCanonical,
  comments: Option[String],
  trackAdded: Instant,
  points: Long,
  avgSpeed: Option[SpeedM],
  avgTemperature: Option[Temperature],
  distance: DistanceM
)

case class DoobieCoord(id: TrackPointId, topSpeed: SpeedM)

case class DoobieBoat(id: DeviceId)

object DoobieTracksDatabase {
  def apply(ds: HikariDataSource, ec: ExecutionContext) = new DoobieTracksDatabase(ds)(ec)
}

class DoobieTracksDatabase(val ds: HikariDataSource)(implicit ec: ExecutionContext) {
  private val log = Logger(getClass)
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private val tx: Resource[IO, Aux[IO, DataSource]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32) // our connect EC
    be <- Blocker[IO] // our blocking EC
  } yield Transactor.fromDataSource[IO](ds, ec, be)
  implicit val logHandler = LogHandler {
    case Success(sql, args, exec, processing) =>
      val dur = (exec + processing).toMillis
      log.info(s"OK '$sql' exec ${exec.toMillis} ms processing ${processing.toMillis} ms.")
    case ProcessingFailure(sql, args, exec, processing, failure) =>
      log.error(s"Failed '$sql' in ${exec + processing}.", failure)
    case ExecFailure(sql, args, exec, failure) =>
      log.error(s"Exec failed '$sql' in $exec.'", failure)
  }
  import doobieMappings._

  object sql {
    val boats =
      sql"""select b.id, b.name, b.token, u.id uid, u.user, u.email, u.language
         from boats b, users u 
         where b.owner = u.id"""
    val topPoints =
      sql"""select winners.track track, min(winners.id) point
          from (select p.id, p.track
                from points p,
                    (select track, max(boat_speed) maxSpeed from points group by track) tops
                where p.track = tops.track and p.boat_speed = tops.maxSpeed) winners
          group by winners.track"""
    val topRows =
      sql"""select id, longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff, added 
           from points p 
           where p.id in (select point from ($topPoints) fastestPoints)"""
    val minMaxTimes =
      sql"""select track,
       min(boat_time)                                        start,
       max(boat_time)                                        end,
       timestampdiff(SECOND, min(boat_time), max(boat_time)) secs,
       date(min(boat_time))                                  startDate,
       month(min(boat_time))                                 startMonth,
       year(min(boat_time))                                  startYear
        from points p
        group by track"""
    val timedTracks =
      sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, times.secs secs, times.start, times.end, times.startDate, times.startMonth, times.startYear, t.boat
           from tracks t,
           ($minMaxTimes) times 
           where t.id = times.track"""
    val trackHighlights =
      sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.start, t.end, t.secs, t.startDate, t.startMonth, t.startYear, t.boat, top.id pointId, top.longitude, top.latitude, top.coord, top.boat_speed, top.water_temp, top.depthm, top.depth_offsetm, top.boat_time, date(top.boat_time) trackDate, top.track, top.added topAdded
            from ($topRows) top, ($timedTracks) t
            where top.track = t.id"""
    val nonEmptyTracks =
      sql"""select t.id tid, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.id trackId, t.start, t.end, t.secs, t.startDate, t.startMonth, t.startYear, t.pointId, t.longitude, t.latitude, t.coord, t.boat_speed topSpeed, t.water_temp, t.depthm, t.depth_offsetm, t.boat_time, t.trackDate, t.track, t.topAdded, b.id bid, b.name boatName, b.token, b.uid, b.user, b.email, b.language
          from ($boats) b, ($trackHighlights) t
          where b.id = t.boat"""
    def tracksBy(user: Username) = sql"""$nonEmptyTracks and user = $user"""
    import com.malliina.boat.http.TrackSort._

    def tracksFor(user: Username, filter: TrackQuery) = {
      val order = if (filter.order == SortOrder.Asc) fr"asc" else fr"desc"
      val sortColumns = filter.sort match {
        case Name     => fr"t.title $order, t.name $order, t.id $order"
        case Recent   => fr"t.start $order, t.id $order"
        case Points   => fr"t.points $order, t.id $order"
        case TopSpeed => fr"topSpeed $order, t.id $order"
        case Length   => fr"t.distance $order, t.id $order"
        case _        => fr"t.start $order, t.id $order"
      }
      val unsorted = tracksBy(user)
      val limits = filter.limits
      sql"""$unsorted order by $sortColumns limit ${limits.limit} offset ${limits.offset}"""
    }
  }

  val boatsView = sql.boats.query[JoinedBoat].to[List]
  val topView = sql.topRows.query[TrackPointRow].to[List]

  def hm: Future[Int] = run {
    sql"select 42".query[Int].unique
  }

  def boats = run { boatsView }
  def topRows = run { topView }
  def nonEmpty = run { sql.nonEmptyTracks.query[(DoobieTrack, CombinedCoord, JoinedBoat)].to[List] }
  def test = run {
    sql
      .tracksFor(Username("mle"), TrackQuery(TrackSort.TopSpeed, SortOrder.Desc, Limits(1, 0)))
      .query[(DoobieTrack, TrackTimes, CombinedCoord, JoinedBoat)]
      .to[List]
      .map { list => list.map { case (track, times, top, boat) => join(track, times, top, boat) } }
  }

  def join(track: DoobieTrack, times: TrackTimes, top: CombinedCoord, boat: JoinedBoat) =
    JoinedTrack(
      track.track,
      track.trackName,
      track.trackTitle,
      track.canonical,
      track.comments,
      track.trackAdded,
      boat,
      track.points.toInt,
      Option(times.start),
      times.date,
      times.month,
      times.year,
      Option(times.`end`),
      times.duration,
      Option(top.boatSpeed),
      track.avgSpeed,
      track.avgTemperature,
      track.distance,
      top
    )

  protected def run[T](io: ConnectionIO[T]): Future[T] =
    tx.use(r => io.transact(r)).unsafeToFuture()
}
