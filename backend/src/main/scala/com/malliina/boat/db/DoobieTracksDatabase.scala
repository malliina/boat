package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.*
import com.malliina.boat.InviteState.accepted
import com.malliina.boat.http.{BoatQuery, SortOrder, TrackQuery}
import com.malliina.boat.*
import com.malliina.database.DoobieDatabase
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.util.AppLogger
import com.malliina.values.Username
import doobie.*
import doobie.implicits.*

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

object DoobieTracksDatabase:
  private val log = AppLogger(getClass)

  private def collectRows(
    rows: Seq[SentenceCoord2],
    formatter: TimeFormatter
  ): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]) { (acc, sc) =>
      val idx = acc.indexWhere(_.id == sc.c.id)
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(
          idx,
          old.copy(sentences = old.sentences :+ sc.s.timed(formatter))
        )
      else acc :+ sc.c.toFull(Seq(sc.s), formatter)
    }

  /** Collects `rows` into coord events in which their order will be reversed.
    */
  private def collectTrackCoords(rows: Seq[TrackCoord], language: Language): Seq[CoordsEvent] =
    val start = System.currentTimeMillis()
    val formatter = TimeFormatter.lang(language)
    val result = rows.foldLeft(Vector.empty[CoordsEvent]) { (acc, tc) =>
      val from = tc.track
      val point = tc.row
      val idx = acc.indexWhere(_.from.track == from.track)
      val instant = point.sourceTime
      val coord = TimedCoord(
        point.id,
        point.coord,
        formatter.formatDateTime(instant),
        instant.toEpochMilli,
        formatter.formatTime(instant),
        point.speed.getOrElse(SpeedM.zero),
        point.altitude,
        point.outsideTemp,
        point.waterTemp.getOrElse(Temperature.zeroCelsius),
        point.depth.getOrElse(DistanceM.zero),
        formatter.timing(instant)
      )
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = coord :: old.coords))
      else acc :+ CoordsEvent(List(coord), from.strip(formatter))
    }
    val end = System.currentTimeMillis()
    val duration = (end - start).millis
    if duration > 500.millis then log.warn(s"Collected ${rows.length} in ${duration.toMillis} ms")
    result

class DoobieTracksDatabase[F[_]: Async](val db: DoobieDatabase[F])
  extends TracksSource[F]
  with StatsSource[F]:
  import Mappings.*
  val F = Async[F]
  object sql extends CommonSql:
    def pointsByTrack(name: TrackName) =
      sql"""select $pointColumns
            from points p, tracks t
            where p.track = t.id and t.name = $name"""
    def pointsByTime(name: TrackName) =
      val selectPoints = pointsByTrack(name)
      sql"""$selectPoints order by p.source_time asc"""
    def topPointByTrack(name: TrackName) =
      val selectPoints = pointsByTrack(name)
      sql"$selectPoints order by p.speed desc limit 1"
    def tracksByUser(user: Username) =
      sql"$nonEmptyTracks and (b.user = $user or b.id in (select ub2.boat from users u2, users_boats ub2 where u2.id = ub2.user and u2.user = $user and ub2.state = $accepted))"
    def trackByName(name: TrackName) = sql"$nonEmptyTracks and t.name = $name"
    def tracksByNames(names: NonEmptyList[TrackName]) =
      sql"$nonEmptyTracks and " ++ Fragments.in(fr"t.name", names)
    def tracksByCanonicals(names: NonEmptyList[TrackCanonical]) =
      sql"$nonEmptyTracks and " ++ Fragments.in(fr"t.canonical", names)
    def latestTracks(name: Username, limit: Option[Int]) =
      val userTracks = tracksByUser(name)
      val limitClause = limit.fold(Fragment.empty)(l => fr"limit $l")
      sql"$userTracks order by t.added desc $limitClause"
    import com.malliina.boat.http.TrackSort.*

    def tracksFor(user: Username, filter: TrackQuery) =
      val order = if filter.order == SortOrder.Asc then fr"asc" else fr"desc"
      val sortColumns = filter.sort match
        case Name     => fr"t.title $order, t.name $order, t.id $order"
        case Recent   => fr"t.start $order, t.id $order"
        case Points   => fr"t.points $order, t.id $order"
        case TopSpeed => fr"topSpeed $order, t.id $order"
        case Length   => fr"t.distance $order, t.id $order"
        case _        => fr"t.start $order, t.id $order"
      val unsorted = tracksByUser(user)
      val limits = filter.limits
      sql"""$unsorted order by $sortColumns limit ${limits.limit} offset ${limits.offset}"""

  private val boatsView = sql.boats.query[JoinedSource].to[List]
  private val topView = sql.topRows.query[TrackPointRow].to[List]

  def hm: F[Option[SpeedM]] = run {
    sql"select avg(speed) from points p where p.speed >= 100 having avg(speed) is not null"
      .query[SpeedM]
      .option
  }

  def boats = run { boatsView }
  def topRows = run { topView }

  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): F[TracksBundle] =
    run {
      for
        ts <- tracksForIO(user, filter)
        ss <- statsIO(user, filter, lang)
      yield TracksBundle(ts.tracks, ss)
    }

  def stats(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): F[StatsResponse] = run {
    statsIO(user, filter, lang)
  }

  private def statsIO(user: MinimalUserInfo, filter: TrackQuery, lang: Lang) =
    val tracks = sql.tracksByUser(user.username)
    val zeroDistance = DistanceM.zero
    val zeroDuration: FiniteDuration = 0.seconds
    val now = DateVal.now()
    val ord = if filter.order == SortOrder.Desc then fr"desc" else fr"asc"
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
    for
      daily <- dailyIO
      monthly <- monthlyIO
      yearly <- yearlyIO
      allTime <- allTimeIO
    yield
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

  def ref(track: TrackName, language: Language): F[TrackRef] =
    single(sql.trackByName(track), language)

  def canonical(trackCanonical: TrackCanonical, language: Language): F[TrackRef] =
    single(sql.tracksByCanonicals(NonEmptyList.of(trackCanonical)), language)

  def track(track: TrackName, user: Username, query: TrackQuery): F[TrackInfo] = run {
    for
      points <- sql.pointsByTime(track).query[CombinedCoord].to[List]
      top <- sql.topPointByTrack(track).query[CombinedCoord].option
    yield TrackInfo(points, top)
  }

  def full(track: TrackName, language: Language, query: TrackQuery): F[FullTrack] = run {
    val rows = sql.pointsByTrack(track)
    val limited =
      sql"""$rows order by p.source_time asc, p.id asc, p.added asc limit ${query.limit} offset ${query.offset}"""
    val coordsIO =
      sql"""select ${sql.pointColumns}, s.id, s.sentence, s.added sentenceAdded
            from ($limited) p, sentence_points sp, sentences s
            where p.id = sp.point and sp.sentence = s.id
            order by p.source_time, p.id, sentenceAdded"""
        .query[SentenceCoord2]
        .to[List]
    val trackIO = sql.trackByName(track).query[JoinedTrack].unique
    val formatter = TimeFormatter.lang(language)
    for
      stats <- trackIO
      coords <- coordsIO
    yield FullTrack(stats.strip(formatter), DoobieTracksDatabase.collectRows(coords, formatter))
  }

  def history(user: MinimalUserInfo, limits: BoatQuery): F[Seq[CoordsEvent]] = run {
    val eligible = limits.neTracks
      .map(names => sql.tracksByNames(names))
      .orElse(limits.neCanonicals.map(cs => sql.tracksByCanonicals(cs)))
      .getOrElse {
        val limit = Option.when(limits.newest)(5)
        sql.latestTracks(user.username, limit)
      }
      .query[JoinedTrack]
      .to[List]
    eligible.flatMap { ts =>
      if ts.isEmpty then List.empty[CoordsEvent].pure[ConnectionIO]
      else
        val conditions = Fragments.whereAndOpt(
          ts.map(_.track).distinct.toNel.map(ids => Fragments.in(fr"p.track", ids)),
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
      row.strip(TimeFormatter.lang(language))
    }
  }

  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): F[Tracks] = run {
    tracksForIO(user, filter)
  }

  private def tracksForIO(user: MinimalUserInfo, filter: TrackQuery) =
    val formatter = TimeFormatter.lang(user.language)
    sql
      .tracksFor(user.username, filter)
      .query[JoinedTrack]
      .to[List]
      .map { list =>
        Tracks(list.map(_.strip(formatter)))
      }

  def run[T](io: ConnectionIO[T]): F[T] = db.run(io)
