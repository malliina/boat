package com.malliina.boat.db

import com.malliina.boat._
import com.malliina.boat.db.TracksDatabase.log
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object TracksDatabase {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): TracksDatabase = new TracksDatabase(db)(ec)
}

class TracksDatabase(val db: BoatSchema)(implicit ec: ExecutionContext) extends TracksSource {

  import db._
  import db.api._
  import db.mappings._

  case class Joined(sid: SentenceKey, sentence: RawSentence, track: TrackId, trackName: TrackName, boat: BoatId, boatName: BoatName, user: UserId, username: User)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], track: Rep[TrackId], trackName: Rep[TrackName], boat: Rep[BoatId], boatName: Rep[BoatName], user: Rep[UserId], username: Rep[User])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  val sentencesView: Query[LiftedJoined, Joined, Seq] = sentencesTable.join(tracksView).on(_.track === _.track)
    .map { case (ss, bs) => LiftedJoined(ss.id, ss.sentence, bs.track, bs.trackName, bs.boat, bs.boatName, bs.user, bs.username) }

  override def join(meta: BoatMeta): Future[JoinedTrack] =
    db.run(boatId(meta))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]] = {
    val from = sentences.from
    val action = db.sentenceInserts ++= sentences.sentences.map { s => SentenceInput(s, from.track) }
    insertLogged(action, from, "sentence")
  }

  override def saveCoords(coords: CoordsEvent): Future[Seq[TrackPointId]] = {
    val from = coords.from
    val action = db.coordInserts ++= coords.coords.map { c => TrackPointInput.forCoord(c, from.track) }
    insertLogged(action, coords.from, "coordinate")
  }

  private def insertLogged[R](action: DBIOAction[Seq[R], NoStream, Nothing], from: JoinedTrack, word: String) = {
    db.run(action).map { keys =>
      val pluralSuffix = if (keys.length > 1) "s" else ""
      log.info(s"Inserted ${keys.length} $word$pluralSuffix from '${from.boatName}' owned by '${from.username}'.")
      keys
    }
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
    } yield {
      log.info(s"Prepared boat '${from.boat}' with ID '${boatRow.id}' for owner '${from.user}'.")
      JoinedTrack(track.id, track.name, boat, boatRow.name, user, userRow.username)
    }

  def prepareTrack(trackName: TrackName, boat: BoatId) =
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
