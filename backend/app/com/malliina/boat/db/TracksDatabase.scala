package com.malliina.boat.db

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import com.malliina.boat._
import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat.http._
import com.malliina.boat.parsing.FullCoord
import com.malliina.measure.Distance
import com.malliina.values.{Email, UserId, Username}
import play.api.Logger

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

object TracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): TracksDatabase = new TracksDatabase(db)(ec)
}

class TracksDatabase(val db: BoatSchema)(implicit ec: ExecutionContext) extends TracksSource {
  val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  import db._
  import db.api._

  case class Joined(sid: SentenceKey, sentence: RawSentence, track: TrackId,
                    trackName: TrackName, boat: BoatId, boatName: BoatName,
                    user: UserId, username: Username)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], track: Rep[TrackId],
                          trackName: Rep[TrackName], boat: Rep[BoatId], boatName: Rep[BoatName],
                          user: Rep[UserId], username: Rep[Username])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  val sentencesView: Query[LiftedJoined, Joined, Seq] = sentencesTable.join(tracksView).on(_.track === _.track)
    .map { case (ss, bs) => LiftedJoined(ss.id, ss.sentence, bs.track, bs.trackName, bs.boat, bs.boatName, bs.user, bs.username) }

  override def join(meta: BoatMeta): Future[JoinedTrack] =
    action(boatId(meta))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[KeyedSentence]] = {
    val from = sentences.from
    val action = DBIO.sequence {
      sentences.sentences.map { s =>
        (sentenceInserts += SentenceInput(s, from.track)).map { key => KeyedSentence(key, s, from) }
      }
    }
    insertLogged(action, from, "sentence")
  }

  override def saveCoords(coord: FullCoord): Future[Seq[TrackPointId]] =
    insertLogged(saveCoordAction(coord).map(id => Seq(id)), coord.from, "coordinate")

  def saveCoordAction(coord: FullCoord) = (for {
    // previous <- pointsTable.filter(_.track === coord.from.track).map(_.trackIndex).max.result
    previous <- pointsTable.filter(_.track === coord.from.track).sortBy(_.trackIndex.desc).take(1).result
    trackIdx = previous.headOption.map(_.trackIndex).getOrElse(0) + 1
    point <- coordInserts += TrackPointInput.forCoord(coord, trackIdx, previous.headOption.map(_.id))
    _ <- sentencePointsTable ++= coord.parts.map(key => SentencePointLink(key, point))
  } yield point).transactionally

  override def tracksFor(email: Email, filter: TrackQuery): Future[TrackSummaries] =
    trackList(tracksViewNonEmpty.filter(t => t.email.isDefined && t.email === email), filter)

  override def tracks(user: Username, filter: TrackQuery): Future[TrackSummaries] =
    trackList(tracksViewNonEmpty.filter(t => t.username === user), filter)

  override def distances(email: Email): Future[Seq[EasyDistance]] = action {
    db.distances.result.map { rows => rows.map { case (t, d) => EasyDistance(t, d.getOrElse(Distance.zero)) }}
  }

  private def trackList(trackQuery: Query[LiftedJoinedTrack, JoinedTrack, Seq], filter: TrackQuery) = action {
    trackQuery.sortBy { ljt =>
      (filter.sort, filter.order) match {
        case (TrackSort.Recent, SortOrder.Desc) => ljt.end.desc.nullsLast
        case (TrackSort.Recent, SortOrder.Asc) => ljt.end.asc.nullsLast
        case (TrackSort.Points, SortOrder.Desc) => ljt.points.desc.nullsLast
        case _ => ljt.points.asc.nullsLast
      }
    }.result.map { rows =>
      val summaries = rows.map { track =>
        val first = track.start.get
        val last = track.end.get
        val firstMillis = first.toEpochMilli
        val lastMillis = last.toEpochMilli
        val duration = (lastMillis - firstMillis).millis
        val firstUtc = first.atOffset(ZoneOffset.UTC)
        val lastUtc = last.atOffset(ZoneOffset.UTC)
        //        val distance = distances.getOrElse(track.track, Distance.zero)
        TrackSummary(track.strip, TrackStats(track.points, timeFormatter.format(firstUtc), firstMillis, timeFormatter.format(lastUtc), lastMillis, duration))
      }
      TrackSummaries(summaries)
    }
  }

  override def track(track: TrackName, email: Email, query: TrackQuery): Future[Seq[CombinedCoord]] = action {
    // intentionally does not filter on email for now
    pointsTable.map(_.combined).join(tracksTable.filter(t => t.name === track)).on(_.track === _.id).map(_._1)
      .sortBy(_.boatTime.asc)
      .result
  }

  override def history(user: Username, limits: BoatQuery): Future[Seq[CoordsEvent]] = action {
    val newestTrack = tracksViewNonEmpty.filter(_.username === user).sortBy(_.start.desc.nullsLast).take(1)
    // Intentionally, you can view any track if you know its key.
    // Alternatively, we could filter tracks by user and make that optional.
    val eligibleTracks =
      if (limits.tracks.nonEmpty) tracksViewNonEmpty.filter(t => t.trackName.inSet(limits.tracks))
      else if (limits.newest) tracksViewNonEmpty.join(newestTrack).on(_.track === _.track).map(_._1)
      else tracksViewNonEmpty
    val query = eligibleTracks
      .join(rangedCoords(limits.timeRange)).on(_.track === _.track)
      .sortBy { case (_, point) => (point.boatTime.desc, point.added.desc, point.id.desc) }
      .drop(limits.offset)
      .take(limits.limit)
      .sortBy { case (_, point) => (point.boatTime.asc, point.added.asc, point.id.asc) }
    query.result.map { rows => collectPoints(rows) }
  }

  override def renameBoat(old: BoatMeta, newName: BoatName): Future[BoatRow] = {
    val action = for {
      id <- db.first(boatsView.filter(b => b.username === old.user && b.boatName === old.boat).map(_.boat), s"Boat not found: '${old.boat}'.")
      _ <- boatsTable.filter(_.id === id).map(_.name).update(newName)
      updated <- db.first(boatsTable.filter(_.id === id), s"Boat not found: '${old.boat}'.")
    } yield updated
    db.run(action).map { maybeBoat =>
      log.info(s"Renamed boat '${old.boat}' owned by '${old.user}' to '$newName'.")
      maybeBoat
    }
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

  private def insertLogged[R](action: DBIOAction[Seq[R], NoStream, Nothing], from: TrackRef, word: String) = {
    db.run(action).map { keys =>
      val pluralSuffix = if (keys.length == 1) "" else "s"
      log.info(s"Inserted ${keys.length} $word$pluralSuffix from '${from.boatName}' owned by '${from.username}'.")
      keys
    }.recoverWith { case t =>
      log.error(s"Error inserting $word from '${from.boatName}'.", t)
      Future.failed(t)
    }
  }

  private def boatId(from: BoatMeta) =
    tracksView.filter(t => t.username === from.user && t.boatName === from.boat && t.trackName === from.track).result.headOption.flatMap { maybeTrack =>
      maybeTrack.map { track =>
        DBIO.successful(track)
      }.getOrElse {
        prepareBoat(from)
      }
    }.transactionally

  private def prepareBoat(from: BoatMeta) =
    for {
      userRow <- db.first(usersTable.filter(_.user === from.user), s"User not found: '${from.user}'.")
      user = userRow.id
      maybeBoat <- boatsTable.filter(b => b.name === from.boat && b.owner === user).result.headOption
      boatRow <- maybeBoat.map(b => DBIO.successful(b)).getOrElse(registerBoat(from, user))
      boat = boatRow.id
      track <- prepareTrack(from.track, boat)
      joined <- db.first(tracksView.filter(_.track === track.id), "Track not found.")
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
      trackId <- trackInserts += TrackInput(trackName, boat)
      track <- db.first(tracksTable.filter(_.id === trackId), s"Track not found: '$trackId'.")
    } yield {
      log.info(s"Registered track with ID '$trackId' for boat '$boat'.")
      track
    }

  private def registerBoat(from: BoatMeta, user: UserId) =
    boatsTable.filter(b => b.name === from.boat).exists.result.flatMap { exists =>
      if (exists) DBIO.failed(new Exception(s"Boat name '${from.boat}' is already taken and therefore not available for '${from.user}'."))
      else saveBoat(from, user)
    }

  private def saveBoat(from: BoatMeta, user: UserId) =
    for {
      boatId <- boatInserts += BoatInput(from.boat, BoatTokens.random(), user)
      boat <- db.first(boatsTable.filter(_.id === boatId), s"Boat not found: '$boatId'.")
    } yield {
      log.info(s"Registered boat '${from.boat}' with ID '${boat.id}' owned by '${from.user}'.")
      boat
    }

  private def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)
}
