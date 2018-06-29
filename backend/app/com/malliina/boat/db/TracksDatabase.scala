package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat.http._
import com.malliina.logbackrx.TimeFormatter
import com.malliina.measure.Distance
import play.api.Logger

import concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

object TracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): TracksDatabase = new TracksDatabase(db)(ec)
}

class TracksDatabase(val db: BoatSchema)(implicit ec: ExecutionContext) extends TracksSource {
  val timeFormatter = new TimeFormatter("yyyy-MM-dd HH:mm:ss")

  import db._
  import db.api._
  import db.mappings._

  case class Joined(sid: SentenceKey, sentence: RawSentence, track: TrackId,
                    trackName: TrackName, boat: BoatId, boatName: BoatName,
                    user: UserId, username: User)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], track: Rep[TrackId],
                          trackName: Rep[TrackName], boat: Rep[BoatId], boatName: Rep[BoatName],
                          user: Rep[UserId], username: Rep[User])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  val sentencesView: Query[LiftedJoined, Joined, Seq] = sentencesTable.join(tracksView).on(_.track === _.track)
    .map { case (ss, bs) => LiftedJoined(ss.id, ss.sentence, bs.track, bs.trackName, bs.boat, bs.boatName, bs.user, bs.username) }

  override def join(meta: BoatMeta): Future[JoinedTrack] =
    db.run(boatId(meta))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]] = {
    val from = sentences.from
    val action = sentenceInserts ++= sentences.sentences.map { s => SentenceInput(s, from.track) }
    insertLogged(action, from, "sentence")
  }

  override def saveCoords(coords: CoordsEvent): Future[Seq[TrackPointId]] = {
    val from = coords.from
    val action = coordInserts ++= coords.coords.map { c => TrackPointInput.forCoord(c, from.track) }
    insertLogged(action, coords.from, "coordinate")
  }

  override def tracks(user: User, filter: TrackQuery): Future[TrackSummaries] = {
    val query = tracksView.filter(t => t.username === user).join(coordsTable).on(_.track === _.track)
      .groupBy { case (t, _) => t }
      .map { case (track, ps) =>
        val points = ps.map(_._2)
        (track, points.length, points.map(_.added).min, points.map(_.added).max)
      }
      .sortBy {
        case (_, points, _, last) => (filter.sort, filter.order) match {
          case (TrackSort.Recent, SortOrder.Desc) => last.desc.nullsLast
          case (TrackSort.Recent, SortOrder.Asc) => last.asc.nullsLast
          case (TrackSort.Points, SortOrder.Desc) => points.desc.nullsLast
          case _ => points.asc.nullsLast
        }
      }
    val action = query.result.map { rows =>
      val summaries = rows.map { case (track, points, first, last) =>
        val firstMillis = first.get.toEpochMilli
        val lastMillis = last.get.toEpochMilli
        val duration = (lastMillis - firstMillis).millis
        TrackSummary(track.strip(Distance.zero), TrackStats(points, timeFormatter.format(firstMillis), firstMillis, timeFormatter.format(lastMillis), lastMillis, duration))
      }
      TrackSummaries(summaries)
    }
    db.run(action)
  }

  override def history(user: User, limits: BoatQuery): Future[Seq[CoordsEvent]] = {
    val newestTrack = tracksViewNonEmpty.filter(_.username === user).sortBy(_.trackAdded.desc).take(1)
    val eligibleTracks =
      if (limits.tracks.nonEmpty) tracksView.filter(t => t.username === user && t.trackName.inSet(limits.tracks))
      else if (limits.newest) tracksView.join(newestTrack).on(_.track === _.track).map(_._1)
      else tracksView
    val query = eligibleTracks
      .join(rangedCoords(limits.timeRange)).on(_.track === _.track)
      .sortBy { case (_, point) => (point.added.desc, point.id.desc) }
      .drop(limits.offset)
      .take(limits.limit)
      .sortBy { case (_, point) => (point.added.asc, point.id.asc) }
    db.run(query.result.map { rows => collectCoords(rows) })
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
    coordsTable.filter { c =>
      limits.from.map(from => c.added >= from).getOrElse(trueColumn) &&
        limits.to.map(to => c.added <= to).getOrElse(trueColumn)
    }

  private def trueColumn: Rep[Boolean] = valueToConstColumn(true)

  private def collectCoords(rows: Seq[(JoinedTrack, TrackPointRow)]): Seq[CoordsEvent] =
    rows.foldLeft(Vector.empty[CoordsEvent]) { case (acc, (from, point)) =>
      val idx = acc.indexWhere(_.from.track == from.track)
      val coord = Coord(point.lon, point.lat)
      if (idx >= 0) {
        val old = acc(idx)
        acc.updated(idx, old.copy(coords = old.coords :+ coord))
      } else {
        acc :+ CoordsEvent(Seq(coord), from.strip(Distance.zero))
      }
    }.map { ce => ce.copy(from = ce.from.copy(distance = Earth.length(ce.coords.toList))) }

  private def insertLogged[R](action: DBIOAction[Seq[R], NoStream, Nothing], from: TrackRef, word: String) = {
    db.run(action).map { keys =>
      val pluralSuffix = if (keys.length == 1) "" else "s"
      log.info(s"Inserted ${keys.length} $word$pluralSuffix from '${from.boatName}' owned by '${from.username}'.")
      keys
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
}
