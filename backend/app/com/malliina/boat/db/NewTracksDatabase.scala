package com.malliina.boat.db

import com.malliina.boat.http.{BoatQuery, SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.{CombinedCoord, CombinedFullCoord, CoordsEvent, FullTrack, JoinedTrack, Lang, Language, MinimalUserInfo, SentenceCoord2, SentenceRow, TimeFormatter, TimedCoord, TrackCanonical, TrackId, TrackInfo, TrackName, TrackRef, Tracks, TracksBundle}
import com.malliina.values.Username
import io.getquill._
import play.api.Logger

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

object NewTracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase], stats: StatsSource): NewTracksDatabase =
    new NewTracksDatabase(db, stats)

  def collectRows(rows: Seq[SentenceCoord2], formatter: TimeFormatter): Seq[CombinedFullCoord] =
    collect(rows.map(sc => (sc.s, sc.c)), formatter)

  def collect(
    rows: Seq[(SentenceRow, CombinedCoord)],
    formatter: TimeFormatter
  ): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]) {
      case (acc, (s, c)) =>
        val idx = acc.indexWhere(_.id == c.id)
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(
            idx,
            old.copy(sentences = old.sentences :+ s.timed(formatter))
          )
        } else {
          acc :+ c.toFull(Seq(s), formatter)
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

class NewTracksDatabase(val db: BoatDatabase[SnakeCase], stats: StatsSource) extends TracksSource {
  import db._
  implicit val ec: ExecutionContext = db.ec

  def tracksFor(user: MinimalUserInfo, filter: TrackQuery): Future[Tracks] =
    performAsync(s"Load tracks for ${user.username}") {
      val unsorted = quote(tracksBy(lift(user.username)))
      val nameDesc =
        runIO(unsorted.sortBy(t => (t.trackTitle, t.trackName, t.track))(Ord.descNullsLast))
      val nameAsc =
        runIO(unsorted.sortBy(t => (t.trackTitle, t.trackName, t.track))(Ord.ascNullsLast))
      val recentDesc = runIO(unsorted.sortBy(t => (t.start, t.track))(Ord.descNullsLast))
      val recentAsc = runIO(unsorted.sortBy(t => (t.start, t.track))(Ord.ascNullsLast))
      val pointsDesc = runIO(unsorted.sortBy(t => (t.points, t.track))(Ord.descNullsLast))
      val pointsAsc = runIO(unsorted.sortBy(t => (t.points, t.track))(Ord.ascNullsLast))
      val topSpeedDesc = runIO(unsorted.sortBy(t => (t.topSpeed, t.track))(Ord.descNullsLast))
      val topSpeedAsc = runIO(unsorted.sortBy(t => (t.topSpeed, t.track))(Ord.ascNullsLast))
      val lengthDesc = runIO(unsorted.sortBy(t => (t.distance, t.track))(Ord.descNullsLast))
      val lengthAsc = runIO(unsorted.sortBy(t => (t.distance, t.track))(Ord.ascNullsLast))
      val rows =
        if (filter.sort == TrackSort.Name) {
          filter.order match {
            case SortOrder.Desc => nameDesc
            case SortOrder.Asc  => nameAsc
          }
        } else {
          (filter.sort, filter.order) match {
            case (TrackSort.Recent, SortOrder.Desc)   => recentDesc
            case (TrackSort.Recent, SortOrder.Asc)    => recentAsc
            case (TrackSort.Points, SortOrder.Desc)   => pointsDesc
            case (TrackSort.Points, SortOrder.Asc)    => pointsAsc
            case (TrackSort.TopSpeed, SortOrder.Desc) => topSpeedDesc
            case (TrackSort.TopSpeed, SortOrder.Asc)  => topSpeedAsc
            case (TrackSort.Length, SortOrder.Desc)   => lengthDesc
            case (TrackSort.Length, SortOrder.Asc)    => lengthAsc
            case _                                    => recentDesc
          }
        }
      val formatter = TimeFormatter(user.language)
      rows.map(rs => Tracks(rs.map(_.strip(formatter))))
    }

  def tracksBundle(user: MinimalUserInfo, filter: TrackQuery, lang: Lang): Future[TracksBundle] = {
    val statsFuture = stats.stats(user, filter, lang)
    val tracksFuture = tracksFor(user, filter)
    for {
      ss <- statsFuture
      ts <- tracksFuture
    } yield TracksBundle(ts.tracks, ss)
  }

  def ref(track: TrackName, language: Language): Future[TrackRef] =
    performAsync(s"Load track $track") {
      first(runIO(namedTrack(lift(track))), s"Track not found: '$track'.")
        .map(_.strip(TimeFormatter(language)))
    }

  def canonical(trackCanonical: TrackCanonical, language: Language): Future[TrackRef] =
    performAsync("Canonical track") {
      val task = runIO(nonEmptyTracks.filter(_.canonical == lift(trackCanonical)))
      first(task, s"Track not found: '$trackCanonical'.").map { t =>
        t.strip(TimeFormatter(language))
      }
    }

  def track(track: TrackName, user: Username, query: TrackQuery): Future[TrackInfo] =
    performAsync(s"Load track $track") {
      val points = quote(pointsQuery(lift(track)))
      for {
        coords <- runIO(points.sortBy(_.boatTime)(Ord.asc))
        top <- runIO(points.sortBy(_.boatSpeed)(Ord.desc).take(1)).map(_.headOption)
      } yield TrackInfo(coords, top)
    }

  def full(track: TrackName, language: Language, query: TrackQuery): Future[FullTrack] = Future {
    val limitedPoints = quote {
      pointsQuery(lift(track))
        .sortBy(p => (p.boatTime, p.id, p.added))(Ord(Ord.asc, Ord.asc, Ord.asc))
        .drop(lift(query.limits.offset))
        .take(lift(query.limits.limit))
    }
    val coordsSql = quote {
      for {
        p <- limitedPoints
        sp <- sentencePointsTable
        if p.id == sp.point
        s <- sentencesTable
        if s.id == sp.sentence
      } yield SentenceCoord2(
        p.id,
        p.lon,
        p.lat,
        p.coord,
        p.boatSpeed,
        p.waterTemp,
        p.depth,
        p.depthOffset,
        p.boatTime,
        p.date,
        p.track,
        p.added,
        s.id,
        s.sentence,
        s.added
      )
    }
    val trackStats =
      run(namedTrack(lift(track))).headOption.getOrElse(fail(s"Track not found: '$track'."))
    val coords = run(
      coordsSql.sortBy(sc => (sc.boatTime, sc.id, sc.sentenceAdded))(
        Ord(Ord.asc, Ord.asc, Ord.asc)
      )
    )
    val formatter = TimeFormatter(language)
    FullTrack(trackStats.strip(formatter), NewTracksDatabase.collectRows(coords, formatter))
  }

  def history(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]] = {
    val keys = (limits.tracks.map(_.name) ++ limits.canonicals.map(_.name)).mkString(", ")
    val describe = if (keys.isEmpty) "" else s"for tracks $keys "
    performAsync(s"Track history ${describe}by user ${user.username}") {
      def pointsSql = quote { ids: Query[TrackId] =>
        rangedCoords(lift(limits.from), lift(limits.to))
          .filter(p => ids.contains(p.track))
          .sortBy(_.trackIndex)(Ord.desc)
          .drop(lift(limits.offset))
          .take(lift(limits.limit))
      }
      def trackSql(ts: IO[List[JoinedTrack], Effect.Read]) =
        for {
          t <- ts
          c <- runIO(quote(pointsSql(liftQuery(t.map(_.track)))))
        } yield {
          val map = t.groupBy(_.track)
          c.flatMap { row => map.get(row.track).map { jts => TrackCoord(jts.head, row) } }
        }

      val defaultEligible = trackSql {
        runIO(
          nonEmptyTracks
            .filter(_.boat.username == lift(user.username))
            .sortBy(_.trackAdded)(Ord.desc)
            .take(1)
        )
      }
      val trackLimited = trackSql {
        runIO(
          nonEmptyTracks.filter(t => liftQuery(limits.tracks).contains(t.trackName))
        )
      }
      val canonicalLimited = trackSql {
        runIO(
          nonEmptyTracks.filter(t => liftQuery(limits.canonicals).contains(t.canonical))
        )
      }
      val fallback = trackSql { runIO(nonEmptyTracks) }
      val eligibleTracks =
        if (limits.tracks.nonEmpty) trackLimited
        else if (limits.canonicals.nonEmpty) canonicalLimited
        else if (limits.newest) defaultEligible
        else fallback
      eligibleTracks.map { rows => NewTracksDatabase.collectTrackCoords(rows, user.language) }
    }
  }
}
