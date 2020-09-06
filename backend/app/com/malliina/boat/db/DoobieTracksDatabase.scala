package com.malliina.boat.db

import cats.data.NonEmptyList
import com.malliina.boat.http.{BoatQuery, SortOrder, TrackQuery}
import com.malliina.boat.{CombinedCoord, CombinedFullCoord, CoordsEvent, DateVal, FullTrack, JoinedBoat, JoinedTrack, Lang, Language, MinimalUserInfo, MonthlyStats, SentenceCoord2, SentenceRow, Stats, StatsResponse, TimeFormatter, TimedCoord, TrackCanonical, TrackId, TrackInfo, TrackName, TrackPointRow, TrackRef, Tracks, TracksBundle, YearlyStats}
import com.malliina.measure.DistanceM
import com.malliina.values.Username
import doobie._
import doobie.implicits._
import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

object DoobieTracksDatabase {
  private val log = Logger(getClass)

  def apply(db: DoobieDatabase): DoobieTracksDatabase = new DoobieTracksDatabase(db)

  def collectRows(
    rows: Seq[SentenceCoord2],
    formatter: TimeFormatter
  ): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]) {
      case (acc, sc) =>
        val idx = acc.indexWhere(_.id == sc.c.id)
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(
            idx,
            old.copy(sentences = old.sentences :+ sc.s.timed(formatter))
          )
        } else {
          acc :+ sc.c.toFull(Seq(sc.s), formatter)
        }
    }

  def collectTrackCoords(rows: Seq[TrackCoord], language: Language): Seq[CoordsEvent] = {
    val start = System.currentTimeMillis()
    val formatter = TimeFormatter(language)
    val result = rows.foldLeft(Vector.empty[CoordsEvent]) {
      case (acc, tc) =>
        val from = tc.track
        val point = tc.row
        val idx = acc.indexWhere(_.from.track == from.track)
        val instant = point.boatTime
        val coord = TimedCoord(
          point.id,
          point.coord,
          formatter.formatDateTime(instant),
          instant.toEpochMilli,
          formatter.formatTime(instant),
          point.boatSpeed,
          point.waterTemp,
          point.depth,
          formatter.timing(instant)
        )
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(coords = coord :: old.coords))
        } else {
          acc :+ CoordsEvent(List(coord), from.strip(formatter))
        }
    }
    val end = System.currentTimeMillis()
    val duration = (end - start).millis
    if (duration > 500.millis) {
      log.warn(s"Collected ${rows.length} in ${duration.toMillis} ms")
    }
    result
  }
}

class DoobieTracksDatabase(val db: DoobieDatabase) extends TracksSource with StatsSource {
  implicit val ec = db.ec
  import DoobieMappings._
  import db.logHandler

  object sql extends CommonSql {
    def pointsByTrack(name: TrackName) =
      sql"""select $pointColumns
           from points p, tracks t 
           where p.track = t.id and t.name = $name"""

    def pointsByTrackId(id: TrackId) =
      sql"""select $pointColumns
            from points p
            where p.track = $id"""
    def pointsByTime(name: TrackName) = {
      val selectPoints = pointsByTrack(name)
      sql"""$selectPoints order by p.boat_time asc"""
    }
    def topPointByTrack(name: TrackName) = {
      val selectPoints = pointsByTrack(name)
      sql"$selectPoints order by p.boat_speed desc limit 1"
    }
    def tracksByUser(user: Username) = sql"$nonEmptyTracks and user = $user"
    def trackByName(name: TrackName) = sql"$nonEmptyTracks and t.name = $name"
    def tracksByNames(names: NonEmptyList[TrackName]) =
      sql"$nonEmptyTracks and " ++ Fragments.in(fr"t.name", names)
    def tracksByCanonicals(names: NonEmptyList[TrackCanonical]) =
      sql"$nonEmptyTracks and " ++ Fragments.in(fr"t.canonical", names)
    def tracksByCanonical(canonical: TrackCanonical) =
      sql"$nonEmptyTracks and canonical = $canonical"
    def latestTrack(name: Username) = {
      val userTracks = tracksByUser(name)
      sql"$userTracks order by t.added desc limit 1"
    }
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
      val unsorted = tracksByUser(user)
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

  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): Future[TracksBundle] =
    run {
      for {
        ts <- tracksForIO(user, filter)
        ss <- statsIO(user, filter, lang)
      } yield TracksBundle(ts.tracks, ss)
    }

  def stats(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): Future[StatsResponse] = run {
    statsIO(user, filter, lang)
  }

  def statsIO(user: MinimalUserInfo, filter: TrackQuery, lang: Lang) = {
    val tracks = sql.tracksByUser(user.username)
    val zeroDistance = DistanceM.zero
    val zeroDuration: FiniteDuration = 0.seconds
    val now = DateVal.now()
    val ord = if (filter.order == SortOrder.Desc) fr"desc" else fr"asc"
    val aggregates =
      fr"sum(t.distance), sum(t.duration), count(t.track), count(distinct(t.startDate))"
    val dailyIO =
      sql"""select t.startDate, $aggregates
          from ($tracks) t 
          group by t.startDate
          order by t.startDate $ord"""
        .query[DailyAggregates]
        .to[List]
    val monthlyIO =
      sql"""select t.startYear, t.startMonth, $aggregates
          from ($tracks) t 
          group by t.startYear, t.startMonth
          order by t.startYear $ord, t.startMonth $ord"""
        .query[MonthlyAggregates]
        .to[List]
    val yearlyIO =
      sql"""select t.startYear, $aggregates
          from ($tracks) t
          group by t.startYear
          order by t.startYear $ord"""
        .query[YearlyAggregates]
        .to[List]
    val allTimeIO = sql"""select min(t.startDate), max(t.startDate), $aggregates
          from ($tracks) t"""
      .query[AllTimeAggregates]
      .to[List]
    for {
      daily <- dailyIO
      monthly <- monthlyIO
      yearly <- yearlyIO
      allTime <- allTimeIO
    } yield {
      val all = allTime.headOption.getOrElse(AllTimeAggregates.empty)
      val months = monthly.map { ma =>
        MonthlyStats(
          lang.calendar.months(ma.month),
          ma.year,
          ma.month,
          ma.tracks,
          ma.distance.getOrElse(zeroDistance),
          ma.duration.getOrElse(zeroDuration),
          ma.days
        )
      }
      StatsResponse(
        daily.map { da =>
          Stats(
            da.date.iso8601,
            da.date,
            da.date.plusDays(1),
            da.tracks,
            da.distance.getOrElse(zeroDistance),
            da.duration.getOrElse(zeroDuration),
            da.days
          )
        },
        yearly.map { ya =>
          YearlyStats(
            ya.year.toString,
            ya.year,
            ya.tracks,
            ya.distance.getOrElse(zeroDistance),
            ya.duration.getOrElse(zeroDuration),
            ya.days,
            months.filter(_.year == ya.year)
          )
        },
        Stats(
          lang.labels.allTime,
          all.from.getOrElse(now),
          all.to.getOrElse(now),
          all.tracks,
          all.distance.getOrElse(zeroDistance),
          all.duration.getOrElse(zeroDuration),
          all.days
        )
      )
    }
  }

  def ref(track: TrackName, language: Language): Future[TrackRef] =
    single(sql.trackByName(track), language)

  def canonical(trackCanonical: TrackCanonical, language: Language): Future[TrackRef] =
    single(sql.tracksByCanonical(trackCanonical), language)

  def track(track: TrackName, user: Username, query: TrackQuery): Future[TrackInfo] = run {
    for {
      points <- sql.pointsByTime(track).query[CombinedCoord].to[List]
      top <- sql.topPointByTrack(track).query[CombinedCoord].option
    } yield TrackInfo(points, top)
  }

  def full(track: TrackName, language: Language, query: TrackQuery): Future[FullTrack] = run {
    val rows = sql.pointsByTrack(track)
    val limited =
      sql"""$rows order by p.boat_time asc, p.id asc, p.added asc limit ${query.limit} offset ${query.offset}"""
    val coordsIO = sql"""select ${sql.pointColumns}, s.id, s.sentence, s.added sentenceAdded
         from ($limited) p, sentence_points sp, sentences s 
         where p.id = sp.point and sp.sentence = s.id
         order by p.boat_time, p.id, sentenceAdded"""
      .query[SentenceCoord2]
      .to[List]
    val trackIO = sql.trackByName(track).query[JoinedTrack].unique
    val formatter = TimeFormatter(language)
    for {
      stats <- trackIO
      coords <- coordsIO
    } yield FullTrack(stats.strip(formatter), DoobieTracksDatabase.collectRows(coords, formatter))
  }

  def history(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]] = run {
    val eligible = limits.neTracks
      .map(names => sql.tracksByNames(names))
      .orElse(limits.neCanonicals.map(cs => sql.tracksByCanonicals(cs)))
      .getOrElse(if (limits.newest) sql.latestTrack(user.username) else sql.nonEmptyTracks)
      .query[JoinedTrack]
      .to[List]
    eligible.flatMap { ts =>
      val conditions = Fragments.whereAndOpt(
        BoatQuery.toNonEmpty(ts.map(_.track).distinct).map(ids => Fragments.in(fr"p.track", ids)),
        limits.from.map(f => fr"p.added >= $f"),
        limits.to.map(t => fr"p.added <= $t")
      )
      sql"""${sql.selectAllPoints} 
         $conditions 
         order by p.track_index desc 
         limit ${limits.limit} 
         offset ${limits.offset}"""
        .query[TrackPointRow]
        .to[List]
        .map { ps =>
          val tracksById = ts.groupBy(_.track)
          val coords = ps.flatMap { pointRow =>
            tracksById.get(pointRow.track).map { ts => TrackCoord(ts.head, pointRow) }
          }
          DoobieTracksDatabase.collectTrackCoords(coords, user.language)
        }
    }
  }

  private def single(oneRowSql: Fragment, language: Language) = run {
    oneRowSql.query[JoinedTrack].unique.map { row =>
      row.strip(TimeFormatter(language))
    }
  }

  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): Future[Tracks] = run {
    tracksForIO(user, filter)
  }

  def tracksForIO(user: MinimalUserInfo, filter: TrackQuery) = {
    val formatter = TimeFormatter(user.language)
    sql
      .tracksFor(user.username, filter)
      .query[JoinedTrack]
      .to[List]
      .map { list =>
        Tracks(list.map(_.strip(formatter)))
      }
  }

  def run[T](io: ConnectionIO[T]): Future[T] = db.run(io)
}
