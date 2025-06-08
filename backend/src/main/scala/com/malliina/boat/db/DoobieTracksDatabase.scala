package com.malliina.boat.db

import cats.data.NonEmptyList
import cats.effect.Async
import cats.implicits.*
import com.malliina.boat.*
import com.malliina.boat.InviteState.accepted
import com.malliina.boat.http.{BoatQuery, LimitLike, Limits, Near, SortOrder, TrackQuery, TracksQuery}
import com.malliina.database.DoobieDatabase
import com.malliina.measure.{DistanceM, SpeedM, Temperature}
import com.malliina.util.AppLogger
import com.malliina.values.Username
import DoobieTracksDatabase.log
import doobie.*
import doobie.implicits.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object DoobieTracksDatabase:
  private val log = AppLogger(getClass)

  private def collectRows(
    rows: Seq[SentenceCoord2],
    formatter: TimeFormatter
  ): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]): (acc, sc) =>
      val idx = acc.indexWhere(_.id == sc.c.id)
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(
          idx,
          old.copy(sentences = old.sentences :+ sc.s.timed(formatter))
        )
      else acc :+ sc.c.toFull(Seq(sc.s), formatter)

  /** Collects `rows` into coord events, maintaining order.
    */
  private def collectTrackCoords(rows: Seq[TrackCoord], language: Language): Seq[CoordsEvent] =
    val formatter = TimeFormatter.lang(language)
    val result = rows.foldLeft(Vector.empty[CoordsEvent]): (acc, tc) =>
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
        point.battery,
        formatter.timing(instant)
      )
      if idx >= 0 then
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = old.coords :+ coord))
      else acc :+ CoordsEvent(List(coord), from.strip(formatter))
    result

class DoobieTracksDatabase[F[_]: Async](val db: DoobieDatabase[F])
  extends TracksSource[F]
  with StatsSource[F]:
  import Mappings.given
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
    def tracksByUser(user: Username, boats: Seq[BoatName], limits: Option[LimitLike]) =
      val boatFilter =
        boats.toList.toNel.map(list => Fragments.in(fr"b.name", list)).getOrElse(fr"true")
      sql"${nonEmptyTracks(limits)} and $boatFilter and (b.user = $user or b.id in (select ub2.boat from users u2, users_boats ub2 where u2.id = ub2.user and u2.user = $user and ub2.state = $accepted))"
    def trackByName(name: TrackName) =
      sql"${nonEmptyTracks(None)} and t.name = $name"
    def trackByNameLatest(name: TrackName) =
      sql"${nonEmptyTracksLatest(None)} and t.name = $name"
    def tracksByNames(names: NonEmptyList[TrackName]) =
      sql"${nonEmptyTracks(None)} and " ++ Fragments.in(fr"t.name", names)
    def tracksByCanonicals(names: NonEmptyList[TrackCanonical]) =
      sql"${nonEmptyTracks(None)} and " ++ Fragments.in(fr"t.canonical", names)
    def tracksNear(near: Near, by: Username, limits: LimitLike) =
      val userTracks = tracksByUser(by, Nil, None)
      sql"$userTracks and t.id in (select p.track from points p where st_distance_sphere(${near.coord}, p.coord) < ${near.radius}) order by t.added desc limit ${limits.limit} offset ${limits.offset}"
    def latestTracks(name: Username, limits: Option[LimitLike]) =
      tracksByUser(name, Nil, limits)
    import com.malliina.boat.http.TrackSort.*

    def tracksFor(user: Username, tracksFilter: TracksQuery) =
      val filter = tracksFilter.query
      val order = if filter.order == SortOrder.Asc then fr"asc" else fr"desc"
      val sortColumns = filter.sort match
        case Name     => fr"t.title $order, t.name $order, t.id $order"
        case Recent   => fr"t.start $order, t.id $order"
        case Points   => fr"t.points $order, t.id $order"
        case TopSpeed => fr"topSpeed $order, t.id $order"
        case Length   => fr"t.distance $order, t.id $order"
        case _        => fr"t.start $order, t.id $order"
      val unsorted = tracksByUser(user, tracksFilter.sources, None)
      val limits = filter.limits
      sql"""$unsorted order by $sortColumns limit ${limits.limit} offset ${limits.offset}"""

  def hm: F[Option[SpeedM]] = run:
    sql"select avg(speed) from points p where p.speed >= 100 having avg(speed) is not null"
      .query[SpeedM]
      .option

  def tracksBundle(user: MinimalUserInfo, filter: TracksQuery, lang: Lang): F[TracksBundle] =
    run:
      for
        ts <- tracksForIO(user, filter)
        ss <- statsIO(user, filter, lang)
      yield TracksBundle(ts.tracks, ss)

  def stats(user: MinimalUserInfo, filter: TracksQuery, lang: Lang): F[StatsResponse] = run:
    statsIO(user, filter, lang)

  private def statsIO(user: MinimalUserInfo, tracksQuery: TracksQuery, lang: Lang) =
    val filter = tracksQuery.query
    val tracks = sql.tracksByUser(user.username, tracksQuery.sources, None)
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
      val months = monthly.map: ma =>
        MonthlyStats(
          lang.calendar.months(ma.month),
          ma.year,
          ma.month,
          ma.tracks,
          ma.distance.getOrElse(zeroDistance),
          ma.duration.getOrElse(zeroDuration),
          ma.days
        )
      StatsResponse(
        daily.map: da =>
          Stats(
            da.date.iso8601,
            da.date,
            da.date.plusDays(1),
            da.tracks,
            da.distance.getOrElse(zeroDistance),
            da.duration.getOrElse(zeroDuration),
            da.days
          ),
        yearly.map: ya =>
          YearlyStats(
            ya.year.toString,
            ya.year,
            ya.tracks,
            ya.distance.getOrElse(zeroDistance),
            ya.duration.getOrElse(zeroDuration),
            ya.days,
            months.filter(_.year == ya.year)
          ),
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

  def ref(track: TrackName): F[TrackRef] =
    single(sql.trackByName(track))

  def details(track: TrackName): F[JoinedTrack] = run:
    sql.trackByNameLatest(track).query[JoinedTrack].unique

  def refOpt(track: TrackName): F[Option[TrackRef]] =
    option(sql.trackByName(track))

  def canonical(trackCanonical: TrackCanonical): F[TrackRef] =
    single(sql.tracksByCanonicals(NonEmptyList.of(trackCanonical)))

  def track(track: TrackName, user: Username, query: TrackQuery): F[TrackInfo] = run:
    for
      points <- sql.pointsByTime(track).query[CombinedCoord].to[List]
      top <- sql.topPointByTrack(track).query[CombinedCoord].option
    yield TrackInfo(points, top)

  def full(track: TrackName, query: TrackQuery): F[FullTrack] = run:
    val rows = sql.pointsByTrack(track)
    val limited =
      sql"""$rows order by p.source_time asc, p.id asc, p.added asc limit ${query.limit} offset ${query.offset}"""
    val sentencesIO =
      sql"""select ${sql.pointColumns}, s.id, s.sentence, s.added sentenceAdded
            from ($limited) p, sentence_points sp, sentences s
            where p.id = sp.point and sp.sentence = s.id
            order by p.source_time, p.id, sentenceAdded"""
        .query[SentenceCoord2]
        .to[List]
    def pointsIO(formatter: TimeFormatter) =
      limited.query[CombinedCoord].to[List].map(rows => rows.map(_.toFull(Nil, formatter)))
    val trackIO = sql.trackByName(track).query[JoinedTrack].unique
    for
      stats <- trackIO
      formatter = TimeFormatter.lang(stats.boat.language)
      sentences <- sentencesIO
      collected = DoobieTracksDatabase.collectRows(sentences, formatter)
      fullCoords <-
        if collected.isEmpty then pointsIO(formatter)
        else collected.pure[ConnectionIO]
    yield FullTrack(stats.strip(formatter), fullCoords)

  def history(user: MinimalUserInfo, query: BoatQuery): F[Seq[CoordsEvent]] = run:
    val tracksLimit = query.tracksLimit.orElse(Option.when(query.newest)(5))
    val limits = Limits(tracksLimit.getOrElse(5), 0)
    val eligible = query.neTracks
      .map(names => sql.tracksByNames(names))
      .orElse(query.neCanonicals.map(cs => sql.tracksByCanonicals(cs)))
      .orElse(
        query.near.map(n => sql.tracksNear(n, user.username, limits))
      )
      .getOrElse:
        sql.latestTracks(user.username, Option(limits))
      .query[JoinedTrack]
      .to[List]
    eligible.flatMap: ts =>
      if ts.isEmpty then List.empty[CoordsEvent].pure[ConnectionIO]
      else
        val conditions = Fragments.whereAndOpt(
          ts.map(_.track).distinct.toNel.map(ids => Fragments.in(fr"p.track", ids)),
          query.from.map(f => fr"p.added >= $f"),
          query.to.map(t => fr"p.added <= $t")
        )
        sql"""${sql.selectAllPoints}
               $conditions
               order by p.added, p.track_index
               limit ${query.limit}
               offset ${query.offset}"""
          .query[TrackPointRow]
          .to[List]
          .map: ps =>
            val tracksById = ts.groupBy(_.track)
            val coords = ps.flatMap: pointRow =>
              tracksById.get(pointRow.track).map(t => TrackCoord(t.head, pointRow))
            val (collected, duration) = Utils.timed:
              DoobieTracksDatabase.collectTrackCoords(coords, user.language)
            if duration > 1.second then
              val tracks = collected.map(_.from.track).distinct.size
              val coords = collected.map(_.coords.size).sum
              log.info(
                s"Collected $coords coords from $tracks tracks for '${user.username}' using query ${query.describe} in ${duration.toMillis} millis."
              )
            collected

  private def single(oneRowSql: Fragment) = run:
    oneRowSql.query[JoinedTrack].unique.map(row => row.strip(TimeFormatter.lang(row.boat.language)))

  private def option(oneRowSql: Fragment) = run:
    oneRowSql
      .query[JoinedTrack]
      .option
      .map(opt => opt.map(row => row.strip(TimeFormatter.lang(row.boat.language))))

  def tracksFor(user: MinimalUserInfo, filter: TracksQuery): F[Tracks] = run:
    tracksForIO(user, filter)

  private def tracksForIO(user: MinimalUserInfo, filter: TracksQuery) =
    val formatter = TimeFormatter.lang(user.language)
    sql
      .tracksFor(user.username, filter)
      .query[JoinedTrack]
      .to[List]
      .map(list => Tracks(list.map(_.strip(formatter))))

  def run[T](io: ConnectionIO[T]): F[T] = db.run(io)
