package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat.http._
import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{Distance, Speed, SpeedInt}
import com.malliina.values.{Email, UserId, Username}
import play.api.Logger
import concurrent.duration.DurationLong

import scala.concurrent.{ExecutionContext, Future}

object TracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): TracksDatabase = new TracksDatabase(db)(ec)
}

class TracksDatabase(val db: BoatSchema)(implicit ec: ExecutionContext)
    extends DatabaseOps(db)
    with TracksSource {

  import db._
  import db.api._

  val minSpeed: Speed = 1.kmh

  case class Joined(sid: SentenceKey,
                    sentence: RawSentence,
                    track: TrackId,
                    trackName: TrackName,
                    boat: BoatId,
                    boatName: BoatName,
                    user: UserId,
                    username: Username)

  case class LiftedJoined(sid: Rep[SentenceKey],
                          sentence: Rep[RawSentence],
                          track: Rep[TrackId],
                          trackName: Rep[TrackName],
                          boat: Rep[BoatId],
                          boatName: Rep[BoatName],
                          user: Rep[UserId],
                          username: Rep[Username])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  override def join(meta: BoatTrackMeta): Future[TrackMeta] =
    action(boatId(meta))

  def addBoat(boat: BoatName, user: UserId): Future[BoatRow] =
    action(saveBoat(boat, user))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]] = {
    val from = sentences.from
    val action = DBIO.sequence {
      sentences.sentences.map { s =>
        (sentenceInserts += SentenceInput(s, from.track)).map { key =>
          KeyedSentence(key, s, from)
        }
      }
    }
    insertLogged(action, from) { ids =>
      val suffix = if (ids.length > 1) "s" else ""
      s"${ids.length} sentence$suffix"
    }
  }

  override def saveCoords(coord: FullCoord): Future[InsertedPoint] =
    insertLogged(saveCoordAction(coord), coord.from)(_ => "one coordinate")

  def saveCoordAction(coord: FullCoord): DBIOAction[InsertedPoint, NoStream, Effect.All] = {
    val track = coord.from.track
    val action = for {
      previous <- pointsTable.filter(_.track === track).sortBy(_.trackIndex.desc).take(1).result
      trackIdx = previous.headOption.map(_.trackIndex).getOrElse(0) + 1
      diff <- previous.headOption
        .map(p => distanceCoords(p.coord, coord.coord.bind).result)
        .getOrElse(DBIO.successful(Distance.zero))
      point <- coordInserts += TrackPointInput.forCoord(coord, trackIdx, diff)
      _ <- sentencePointsTable ++= coord.parts.map(key => SentencePointLink(key, point))
      // Updates aggregates; simulates a materialized view for performance
      trackQuery = tracksTable.filter(t => t.id === track)
      pointsQuery = pointsTable.filter(p => p.track === track)
      avgSpeed <- pointsQuery.filter(_.boatSpeed >= minSpeed).map(_.boatSpeed).avg.result
      _ <- trackQuery.map(_.avgSpeed).update(avgSpeed)
      avgWaterTemp <- pointsQuery.map(_.waterTemp).avg.result
      _ <- trackQuery.map(_.avgWaterTemp).update(avgWaterTemp)
      points <- pointsQuery.length.result
      _ <- trackQuery.map(_.points).update(points)
      distance <- pointsQuery.map(_.diff).sum.result
      _ <- trackQuery.map(_.distance).update(distance.getOrElse(Distance.zero))
      ref: JoinedTrack <- first(tracksViewNonEmpty.filter(_.track === track),
                                s"Track not found: '$track'.")
    } yield {
      InsertedPoint(point, ref)
    }
    action.transactionally
  }

  override def tracksFor(email: Email, filter: TrackQuery): Future[Tracks] =
    trackList(tracksViewNonEmpty.filter(t => t.email.isDefined && t.email === email), email, filter)

  private def trackList(trackQuery: Query[LiftedJoinedTrack, JoinedTrack, Seq],
                        email: Email,
                        filter: TrackQuery): Future[Tracks] = action {
    val sortedTracksAction = trackQuery.sortBy { ljt =>
      (filter.sort, filter.order) match {
        case (TrackSort.Recent, SortOrder.Desc) => ljt.end.desc.nullsLast
        case (TrackSort.Recent, SortOrder.Asc)  => ljt.end.asc.nullsLast
        case (TrackSort.Points, SortOrder.Desc) => ljt.points.desc.nullsLast
        case _                                  => ljt.points.asc.nullsLast
      }
    }
    for {
      user <- userFor(email)
      rows <- sortedTracksAction.result
    } yield Tracks(rows.map(_.strip(TimeFormatter(user.language))))
  }

  override def track(track: TrackName, email: Email, query: TrackQuery): Future[TrackInfo] =
    action {
      // intentionally does not filter on email for now
      val points = pointsTable
        .map(_.combined)
        .join(tracksTable.filter(t => t.name === track))
        .on(_.track === _.id)
        .map(_._1)
      for {
        coords <- points.sortBy(p => p.boatTime.asc).result
        top <- points.sortBy(_.boatSpeed.desc).take(1).result.headOption
      } yield TrackInfo(coords, top)
    }

  override def full(track: TrackName, email: Email, query: TrackQuery): Future[FullTrack] = action {
    val limitedPoints = pointsTable
      .map(_.combined)
      .join(tracksTable.filter(t => t.name === track))
      .on(_.track === _.id)
      .map(_._1)
      .sortBy(p => (p.boatTime.asc, p.id.asc, p.added.asc))
      .drop(query.limits.offset)
      .take(query.limits.limit)
    val coordsAction = sentencesTable
      .join(sentencePointsTable)
      .on(_.id === _.sentence)
      .join(limitedPoints)
      .on(_._2.point === _.id)
      .map { case ((s, _), p) => (s, p) }
      .sortBy { case (s, p) => (p.boatTime.asc, p.id.asc, s.added.asc) }
      .result
    for {
      user <- userFor(email)
      trackStats <- namedTrack(track)
      coords <- coordsAction
    } yield {
      val formatter = TimeFormatter(user.language)
      FullTrack(trackStats.strip(formatter), collect(coords, formatter))
    }
  }

  override def ref(track: TrackName, email: Email): Future[TrackRef] = action {
    for {
      user <- userFor(email)
      joined <- namedTrack(track)
    } yield joined.strip(TimeFormatter(user.language))
  }

  override def canonical(track: TrackCanonical, email: Email): Future[TrackRef] = action {
    for {
      user <- userFor(email)
      joined <- first(tracksViewNonEmpty.filter(t => t.canonical === track),
                      s"Track not found: '$track'.")
    } yield joined.strip(TimeFormatter(user.language))
  }

  private def userFor(email: Email) =
    first(usersTable.filter(_.email === email), s"Email not found: '$email'.")

  private def namedTrack(track: TrackName) =
    first(tracksViewNonEmpty.filter(_.trackName === track), s"Track not found: '$track'.")

  private def collect(rows: Seq[(SentenceRow, CombinedCoord)],
                      formatter: TimeFormatter): Seq[CombinedFullCoord] =
    rows.foldLeft(Vector.empty[CombinedFullCoord]) {
      case (acc, (s, c)) =>
        val idx = acc.indexWhere(_.id == c.id)
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(sentences = old.sentences :+ s.timed(formatter)))
        } else {
          acc :+ c.toFull(Seq(s), formatter)
        }
    }

  /** Implementation: historyRows returns the coordinates oldest first from the database, then
    * collectPointsClassic collects them by appending - which reverses the order - therefore,
    * the returned CoordsEvent has coordinates ordered newest first.
    */
  override def history(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]] =
    historyRows(user, limits).map { rows =>
      collectPointsClassic(rows, user.language)
    }

  // Returns the coordinates last first
  def historyRows(user: MinimalUserInfo, limits: BoatQuery) = {
    val keys = (limits.tracks.map(_.name) ++ limits.canonicals.map(_.name)).mkString(", ")
    val describe = if (keys.isEmpty) "" else s"for tracks $keys"
    action(s"Track history $describe by user ${user.username}") {
      // Intentionally, you can view any track if you know its key.
      // Alternatively, we could filter tracks by user and make that optional.
      val eligibleTracks =
        if (limits.tracks.nonEmpty)
          tracksViewNonEmpty.filter(_.trackName.inSet(limits.tracks))
        else if (limits.canonicals.nonEmpty)
          tracksViewNonEmpty.filter(_.canonical.inSet(limits.canonicals))
        else if (limits.newest)
          tracksViewNonEmpty.filter(_.username === user.username).sortBy(_.trackAdded.desc).take(1)
        else
          tracksViewNonEmpty
      //    eligibleTracks.result.statements.toList foreach println
      eligibleTracks
        .join(rangedCoords(limits.timeRange))
        .on(_.track === _.track)
        .sortBy { case (_, point) => point.trackIndex.desc }
        .drop(limits.offset)
        .take(limits.limit)
        .result
    }
  }

  def collectPointsClassic(rows: Seq[(JoinedTrack, TrackPointRow)],
                           language: Language): Seq[CoordsEvent] = {
    val start = System.currentTimeMillis()
    val formatter = TimeFormatter(language)
    val result = rows.foldLeft(Vector.empty[CoordsEvent]) {
      case (acc, (from, point)) =>
        val idx = acc.indexWhere(_.from.track == from.track)
        val coord = TimedCoord(
          point.id,
          Coord(point.lon, point.lat),
          formatter.formatDateTime(point.boatTime),
          point.boatTime.toEpochMilli,
          formatter.formatTime(point.boatTime),
          point.boatSpeed,
          point.waterTemp,
          point.depth,
          formatter.timing(point.boatTime)
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

  def modifyTitle(track: TrackName, title: TrackTitle, user: UserId): Future[JoinedTrack] = {
    val action = for {
      id <- first(
        tracksViewNonEmpty.filter(t => t.trackName === track && t.user === user).map(_.track),
        s"Track not found: '$track'.")
      _ <- tracksTable
        .filter(_.id === id)
        .map(t => (t.canonical, t.title))
        .update((TrackCanonical(Utils.normalize(title.title)), Option(title)))
      updated <- first(tracksViewNonEmpty.filter(_.track === id), s"Track ID not found: '$id'.")
    } yield {
      log.info(s"Modified title of track '$id' to '$title' normalized to '${updated.canonical}'.")
      updated
    }
    db.run(action.transactionally)
  }

  override def renameBoat(boat: BoatId, newName: BoatName, user: UserId): Future[BoatRow] = {
    val action = for {
      id <- db.first(boatsView.filter(b => b.user === user && b.boat === boat).map(_.boat),
                     s"Boat not found: '$boat'.")
      _ <- boatsTable.filter(_.id === id).map(_.name).update(newName)
      updated <- first(boatsTable.filter(_.id === id), s"Boat not found: '$id'.")
    } yield {
      log.info(s"Renamed boat '$id' to '$newName'.")
      updated
    }
    db.run(action.transactionally)
  }

  private def rangedCoords(limits: TimeRange) =
    pointsTable.filter { c =>
      limits.from.map(from => c.added >= from).getOrElse(trueColumn) &&
      limits.to.map(to => c.added <= to).getOrElse(trueColumn)
    }

  private def trueColumn: Rep[Boolean] = valueToConstColumn(true)

  def historyNextGen(user: MinimalUserInfo, limits: BoatQuery): Future[Seq[CoordsEvent]] =
    action(s"Fast track history for ${user.username}") {
      val eligibleTracks =
        if (limits.tracks.nonEmpty)
          tracksViewNonEmpty.filter(_.trackName.inSet(limits.tracks))
        else if (limits.canonicals.nonEmpty)
          tracksViewNonEmpty.filter(_.canonical.inSet(limits.canonicals))
        else if (limits.newest)
          tracksViewNonEmpty.filter(_.username === user.username).sortBy(_.trackAdded.desc).take(1)
        else
          tracksViewNonEmpty
      def points(trackIds: Seq[TrackId]) = pointsTable.filter { point =>
        (if (trackIds.nonEmpty) point.track.inSet(trackIds) else trueColumn) &&
        limits.from.map(from => point.added >= from).getOrElse(trueColumn) &&
        limits.to.map(to => point.added <= to).getOrElse(trueColumn)
      }.sortBy { point =>
        point.trackIndex.desc
      }.drop(limits.offset)
        .take(limits.limit)
        .sortBy { point =>
          point.trackIndex.asc
        }
      for {
        ts <- eligibleTracks.result
        ps <- points(ts.map(_.track)).result
      } yield collectPointsNextGen(ts, ps, user.language)
    }

  private def collectPointsNextGen(tracks: Seq[JoinedTrack],
                                   points: Seq[TrackPointRow],
                                   language: Language) = {
    val formatter = TimeFormatter(language)
    val ts = tracks.groupBy(_.track).collect {
      case (key, head +: _) => key -> head
    }
    points.foldLeft(Vector.empty[CoordsEvent]) { (acc, point) =>
      val idx = acc.indexWhere(_.from.track == point.track)
      val coord = TimedCoord(
        point.id,
        Coord(point.lon, point.lat),
        formatter.formatDateTime(point.boatTime),
        point.boatTime.toEpochMilli,
        formatter.formatTime(point.boatTime),
        point.boatSpeed,
        point.waterTemp,
        point.depth,
        formatter.timing(point.boatTime)
      )
      if (idx >= 0) {
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = old.coords :+ coord))
      } else {
        ts.get(point.track).fold(acc) { track =>
          acc :+ CoordsEvent(List(coord), track.strip(formatter))
        }
      }
    }
  }

  private def insertLogged[R](action: DBIOAction[R, NoStream, Nothing], from: TrackMetaLike)(
      describe: R => String): Future[R] = {
    db.run(action)
      .map { keys =>
        log.info(s"Inserted ${describe(keys)} from '${from.boatName}'.")
        keys
      }
      .recoverWith {
        case t =>
          log.error(s"Error inserting data for '${from.boatName}'.", t)
          Future.failed(t)
      }
  }

  private def boatId(from: BoatTrackMeta) =
    trackMetas
      .filter(t =>
        t.username === from.user && t.boatName === from.boat && t.trackName === from.track)
      .result
      .headOption
      .flatMap { maybeTrack =>
        maybeTrack.map { track =>
          DBIO.successful(track)
        }.getOrElse {
          prepareBoat(from)
        }
      }
      .transactionally

  private def prepareBoat(from: BoatTrackMeta) =
    for {
      userRow <- db.first(usersTable.filter(_.user === from.user),
                          s"User not found: '${from.user}'.")
      user = userRow.id
      maybeBoat <- boatsTable
        .filter(b => b.name === from.boat && b.owner === user)
        .result
        .headOption
      boatRow <- maybeBoat.map(b => DBIO.successful(b)).getOrElse(registerBoat(from, user))
      boat = boatRow.id
      track <- prepareTrack(from.track, boat)
      joined <- db.first(trackMetas.filter(_.track === track.id), "Track not found.")
    } yield {
      log.info(s"Prepared boat '${from.boat}' with ID '${boatRow.id}' for owner '${from.user}'.")
      joined
    }

  private def prepareTrack(trackName: TrackName, boat: BoatId) =
    for {
      maybeTrack <- tracksTable
        .filter(t => t.name === trackName && t.boat === boat)
        .result
        .headOption
      track <- maybeTrack.map(t => DBIO.successful(t)).getOrElse(saveTrack(trackName, boat))
    } yield track

  private def saveTrack(trackName: TrackName, boat: BoatId) =
    for {
      trackId <- trackInserts += TrackInput.empty(trackName, boat)
      track <- db.first(tracksTable.filter(_.id === trackId), s"Track not found: '$trackId'.")
    } yield {
      log.info(s"Registered track with ID '$trackId' for boat '$boat'.")
      track
    }

  private def registerBoat(from: BoatTrackMeta, user: UserId) =
    boatsTable.filter(b => b.name === from.boat).exists.result.flatMap { exists =>
      if (exists)
        fail(
          s"Boat name '${from.boat}' is already taken and therefore not available for '${from.user}'.")
      else saveBoat(from, user)
    }

  private def saveBoat(from: BoatMeta, user: UserId): DBIOAction[BoatRow, NoStream, Effect.All] =
    saveBoat(from.boat, user)

  private def saveBoat(name: BoatName, user: UserId): DBIOAction[BoatRow, NoStream, Effect.All] =
    for {
      boatId <- boatInserts += BoatInput(name, BoatTokens.random(), user)
      boat <- db.first(boatsTable.filter(_.id === boatId), s"Boat not found: '$boatId'.")
    } yield {
      log.info(s"Registered boat '$name' with ID '${boat.id}' owned by '$user'.")
      boat
    }

  private def fail(message: String) = DBIO.failed(new Exception(message))
}
