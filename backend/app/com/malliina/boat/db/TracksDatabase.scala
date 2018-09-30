package com.malliina.boat.db

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import com.malliina.boat._
import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat.http._
import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.{Distance, Speed, SpeedInt}
import com.malliina.values.{Email, UserId, Username}
import play.api.Logger

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

object TracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): TracksDatabase = new TracksDatabase(db)(ec)
}

class TracksDatabase(val db: BoatSchema)(implicit ec: ExecutionContext)
  extends TracksSource {
  val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  import db._
  import db.api._

  val minSpeed: Speed = 1.kmh

  case class Joined(sid: SentenceKey, sentence: RawSentence, track: TrackId,
                    trackName: TrackName, boat: BoatId, boatName: BoatName,
                    user: UserId, username: Username)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], track: Rep[TrackId],
                          trackName: Rep[TrackName], boat: Rep[BoatId], boatName: Rep[BoatName],
                          user: Rep[UserId], username: Rep[Username])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  override def join(meta: BoatTrackMeta): Future[TrackMeta] =
    action(boatId(meta))

  def addBoat(boat: BoatName, user: UserId): Future[BoatRow] =
    action(saveBoat(boat, user))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]] = {
    val from = sentences.from
    val action = DBIO.sequence {
      sentences.sentences.map { s =>
        (sentenceInserts += SentenceInput(s, from.track)).map { key => KeyedSentence(key, s, from) }
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
      point <- coordInserts += TrackPointInput.forCoord(coord, trackIdx, previous.headOption.map(_.id), diff)
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
      ref <- first(tracksViewNonEmpty.filter(_.track === track), s"Track not found: '$track'.")
    } yield {
      InsertedPoint(point, ref.strip)
    }
    action.transactionally
  }

  override def tracksFor(email: Email, filter: TrackQuery): Future[TrackSummaries] =
    trackList(tracksViewNonEmpty.filter(t => t.email.isDefined && t.email === email), filter)

  override def tracks(user: Username, filter: TrackQuery): Future[TrackSummaries] =
    trackList(tracksViewNonEmpty.filter(t => t.username === user), filter)

  override def summary(track: TrackName): Future[TrackSummary] = action {
    first(tracksViewNonEmpty.filter(_.trackName === track), s"Track not found: '$track'.").map(trackSummary)
  }

  override def distances(email: Email): Future[Seq[EasyDistance]] = action {
    tracksTable.result.map { rows => rows.map { row => EasyDistance(row.id, row.distance) } }
  }

  private def trackList(trackQuery: Query[LiftedJoinedTrack, JoinedTrack, Seq], filter: TrackQuery) = action {
    val query = trackQuery.sortBy { ljt =>
      (filter.sort, filter.order) match {
        case (TrackSort.Recent, SortOrder.Desc) => ljt.end.desc.nullsLast
        case (TrackSort.Recent, SortOrder.Asc) => ljt.end.asc.nullsLast
        case (TrackSort.Points, SortOrder.Desc) => ljt.points.desc.nullsLast
        case _ => ljt.points.asc.nullsLast
      }
    }
    //    query.result.statements.toList foreach println
    query.result.map { rows =>
      TrackSummaries(rows.map(trackSummary))
    }
  }

  private def trackSummary(track: JoinedTrack) = {
    val first = track.start.get
    val last = track.end.get
    val firstMillis = first.toEpochMilli
    val lastMillis = last.toEpochMilli
    val duration = (lastMillis - firstMillis).millis
    val firstUtc = first.atOffset(ZoneOffset.UTC)
    val lastUtc = last.atOffset(ZoneOffset.UTC)
    TrackSummary(track.strip, TrackStats(track.points, timeFormatter.format(firstUtc), firstMillis, timeFormatter.format(lastUtc), lastMillis, duration))
  }

  override def track(track: TrackName, email: Email, query: TrackQuery): Future[Seq[CombinedCoord]] = action {
    // intentionally does not filter on email for now
    pointsTable.map(_.combined).join(tracksTable.filter(t => t.name === track)).on(_.track === _.id).map(_._1)
      .sortBy(_.boatTime.asc)
      .result
  }

  override def history(user: Username, limits: BoatQuery): Future[Seq[CoordsEvent]] = action {
    val newestTrack = usersTable.filter(_.user === user)
      .join(boatsTable).on(_.id === _.owner)
      .join(tracksTable).on(_._2.id === _.boat)
      .sortBy(_._2.added.desc)
      .map(_._2)
      .take(1)
    //    newestTrack.result.statements.toList foreach println
    // Intentionally, you can view any track if you know its key.
    // Alternatively, we could filter tracks by user and make that optional.
    val eligibleTracks =
    if (limits.tracks.nonEmpty) tracksViewNonEmpty.filter(t => t.trackName.inSet(limits.tracks))
    else if (limits.newest) tracksViewNonEmpty.join(newestTrack).on(_.track === _.id).map(_._1)
    else tracksViewNonEmpty
    //    eligibleTracks.result.statements.toList foreach println
    val query = eligibleTracks
      .join(rangedCoords(limits.timeRange)).on(_.track === _.track)
      .sortBy { case (_, point) => point.trackIndex.desc }
      .drop(limits.offset)
      .take(limits.limit)
      .sortBy { case (_, point) => point.trackIndex.asc }
    //    query.result.statements.toList foreach println
    query.result.map { rows => collectPoints(rows) }
  }

  override def renameBoat(boat: BoatId, user: UserId, newName: BoatName): Future[BoatRow] = {
    val action = for {
      id <- db.first(boatsView.filter(b => b.user === user && b.boat === boat).map(_.boat), s"Boat not found: '$boat'.")
      _ <- boatsTable.filter(_.id === id).map(_.name).update(newName)
      updated <- db.first(boatsTable.filter(_.id === id), s"Boat not found: '$id'.")
    } yield {
      log.info(s"Renamed boat '$id' to '$newName'.")
      updated
    }
    db.run(action)
  }

  private def rangedCoords(limits: TimeRange) =
    pointsTable.filter { c =>
      limits.from.map(from => c.added >= from).getOrElse(trueColumn) &&
        limits.to.map(to => c.added <= to).getOrElse(trueColumn)
    }

  private def trueColumn: Rep[Boolean] = valueToConstColumn(true)

  private def collectPoints(rows: Seq[(JoinedTrack, TrackPointRow)]): Seq[CoordsEvent] =
    rows.foldLeft(Vector.empty[CoordsEvent]) { case (acc, (from, point)) =>
      val idx = acc.indexWhere(_.from.track == from.track)
      val coord = TimedCoord(
        point.id,
        Coord(point.lon, point.lat),
        Instants.format(point.boatTime),
        point.boatTime.toEpochMilli,
        point.boatSpeed,
        point.waterTemp,
        point.depth
      )
      if (idx >= 0) {
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = old.coords :+ coord))
      } else {
        acc :+ CoordsEvent(Seq(coord), from.strip)
      }
    }

  private def insertLogged[R](action: DBIOAction[R, NoStream, Nothing], from: TrackMetaLike)(describe: R => String): Future[R] = {
    db.run(action).map { keys =>
      //      val pluralSuffix = if (keys.length == 1) "" else "s"
      //      log.info(s"Inserted ${describe(keys)} $word$pluralSuffix from '${from.boatName}' owned by '${from.username}'.")
      log.info(s"Inserted ${describe(keys)} from '${from.boatName}' owned by '${from.username}'.")
      keys
    }.recoverWith { case t =>
      log.error(s"Error inserting data for '${from.boatName}'.", t)
      Future.failed(t)
    }
  }

  private def boatId(from: BoatTrackMeta) =
    trackMetas.filter(t => t.username === from.user && t.boatName === from.boat && t.trackName === from.track).result.headOption.flatMap { maybeTrack =>
      maybeTrack.map { track =>
        DBIO.successful(track)
      }.getOrElse {
        prepareBoat(from)
      }
    }.transactionally

  private def prepareBoat(from: BoatTrackMeta) =
    for {
      userRow <- db.first(usersTable.filter(_.user === from.user), s"User not found: '${from.user}'.")
      user = userRow.id
      maybeBoat <- boatsTable.filter(b => b.name === from.boat && b.owner === user).result.headOption
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
      maybeTrack <- tracksTable.filter(t => t.name === trackName && t.boat === boat).result.headOption
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
      if (exists) DBIO.failed(new Exception(s"Boat name '${from.boat}' is already taken and therefore not available for '${from.user}'."))
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

  private def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)
}
