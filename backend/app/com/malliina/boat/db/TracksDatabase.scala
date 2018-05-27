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

  case class Joined(sid: SentenceKey, sentence: RawSentence, boat: BoatId, boatName: BoatName, user: UserId, username: User)

  case class LiftedJoined(sid: Rep[SentenceKey], sentence: Rep[RawSentence], boat: Rep[BoatId], boatName: Rep[BoatName], user: Rep[UserId], username: Rep[User])

  implicit object JoinedShape extends CaseClassShape(LiftedJoined.tupled, Joined.tupled)

  val sentencesView: Query[LiftedJoined, Joined, Seq] = sentencesTable.join(boatsView).on(_.boat === _.boat)
    .map { case (ss, bs) => LiftedJoined(ss.id, ss.sentence, bs.boat, bs.boatName, bs.user, bs.username) }

  override def registerBoat(meta: BoatMeta): Future[BoatRow] = {
    db.run(boatId(meta))
  }

  override def saveSentences(sentences: SentencesEvent): Future[Seq[SentenceKey]] = {
    val from = sentences.from
    val action = db.sentenceInserts ++= sentences.sentences.map { s => SentenceInput(s, sentences.from.boatId) }
    db.run(action).map { keys =>
      log.info(s"Inserted ${keys.length} sentences from '${from.boat}' owned by '${from.user}'.")
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

  private def boatId(from: BoatMeta) = {
    boatsTable.filter(_.name === from.boat).join(usersTable.filter(_.user === from.user)).on(_.owner === _.id).map(_._1).result.headOption.flatMap { maybeBoat =>
      maybeBoat.map { boat =>
        DBIO.successful(boat)
      }.getOrElse {
        boatsTable.filter(b => b.name === from.boat).exists.result.flatMap { exists =>
          if (exists) DBIO.failed(new Exception(s"Boat name '${from.boat}' is already taken and therefore not available for '${from.user}'."))
          else saveBoat(from)
        }
      }
    }.transactionally
  }

  private def saveBoat(from: BoatMeta) = {
    val action = for {
      user <- db.first(usersTable.filter(_.user === from.user).map(_.id), s"User not found: '${from.user}'.")
      boatId <- boatInserts += BoatInput(from.boat, BoatTokens.random(), user)
      boat <- db.first(boatsTable.filter(_.id === boatId), s"Boat not found: '$boatId'.")
    } yield boat
    action.map { boat =>
      log.info(s"Registered boat '${from.boat}' with ID '${boat.id}' for owner '${from.user}'.")
      boat
    }
  }
}
