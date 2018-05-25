package com.malliina.boat.db

import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat._
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

  case class Joined(sid: SentenceKey, sentence: RawSentence, boat: BoatId, boatName: BoatName, user: UserId, username: User)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], boat: Rep[BoatId], boatName: Rep[BoatName], user: Rep[UserId], username: Rep[User])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  case class JoinedBoat(boat: BoatId, boatName: BoatName, user: UserId, username: User)

  case class LiftedJoinedBoat(boat: Rep[BoatId], boatName: Rep[BoatName], user: Rep[UserId], username: Rep[User])

  implicit object JoinedBoatShape extends CaseClassShape(LiftedJoinedBoat.tupled, JoinedBoat.tupled)

  val boatsView: Query[LiftedJoinedBoat, JoinedBoat, Seq] = boatsTable.join(usersTable).on(_.owner === _.id)
    .map { case (b, u) => LiftedJoinedBoat(b.id, b.name, u.id, u.user) }

  val sentencesView: Query[LiftedJoined, Joined, Seq] = sentencesTable.join(boatsView).on(_.boat === _.boat)
    .map { case (ss, bs) => LiftedJoined(ss.id, ss.sentence, bs.boat, bs.boatName, bs.user, bs.username) }

  override def registerBoat(meta: TrackMeta): Future[BoatId] = db.run(boatId(meta))

  override def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]] = {
    val from = sentences.from
    val action = for {
      bid <- boatId(from)
      sids <- db.sentenceInserts ++= sentences.sentences.map { s => SentenceInput(s, bid) }
    } yield sids
    db.run(action).map { keys =>
      log.info(s"Inserted ${keys.length} sentences from '${from.boat}' owned by '${from.user}'.")
      keys
    }
  }

  override def renameBoat(old: TrackMeta, newName: BoatName): Future[BoatRow] = {
    val action = for {
      maybeId <- boatsView.filter(b => b.username === old.user && b.boatName === old.boat).map(_.boat).result.headOption
      id <- maybeId.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(s"Boat not found: '${old.boat}'.")))
      _ <- boatsTable.filter(_.id === id).map(_.name).update(newName)
      maybeUpdated <- boatsTable.filter(_.id === id).result.headOption
      updated <- maybeUpdated.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(s"Boat not found: '${old.boat}'.")))
    } yield updated
    db.run(action).map { maybeBoat =>
      log.info(s"Renamed boat '${old.boat}' owned by '${old.user}' to '$newName'.")
      maybeBoat
    }
  }

  private def boatId(from: TrackMeta) = {
    boatsView.filter(view => view.boatName === from.boat && view.username === from.user).map(_.boat).result.headOption.flatMap { maybeId =>
      maybeId.map { id =>
        DBIO.successful(id)
      }.getOrElse {
        boatsTable.filter(b => b.name === from.boat).exists.result.flatMap { exists =>
          if (exists) DBIO.failed(new Exception(s"Boat name '${from.boat}' is already taken and therefore not available for '${from.user}'."))
          else saveBoat(from)
        }
      }
    }.transactionally
  }

  private def saveBoat(from: TrackMeta) = {
    val action = for {
      maybeUser <- usersTable.filter(_.user === from.user).map(_.id).result.headOption
      user <- maybeUser.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(s"User not found: '${from.user}'.")))
      boatId <- boatInserts += BoatInput(from.boat, user)
    } yield boatId
    action.map { boatId =>
      log.info(s"Registered boat '${from.boat}' with ID '$boatId' for owner '${from.user}'.")
      boatId
    }
  }
}
