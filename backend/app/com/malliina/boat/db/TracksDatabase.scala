package com.malliina.boat.db

import com.malliina.boat.db.TracksDatabase.log
import com.malliina.boat.{BoatId, BoatInput, BoatName, RawSentence, SentenceInput, SentenceKey, SentencesEvent, TrackMeta, User, UserId}
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

  val joined = sentencesTable.join(boatsTable).on(_.boat === _.id).join(usersTable).on(_._2.owner === _.id)
    .map { case ((ss, bs), us) => LiftedJoined(ss.id, ss.sentence, bs.id, bs.name, us.id, us.user) }

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

  private def boatId(from: TrackMeta) = {
    joined.filter(view => view.boatName === from.boat && view.username === from.user).map(_.boat).result.headOption.flatMap { maybeId =>
      maybeId.map { id =>
        DBIO.successful(id)
      }.getOrElse {
        boatsTable.filter(_.name === from.boat).exists.result.flatMap { exists =>
          if (exists) DBIO.failed(new Exception(s"Boat name ${from.boat} is already taken."))
          else saveBoat(from)
        }
      }
    }
  }

  private def saveBoat(from: TrackMeta) = {
    for {
      maybeUser <- usersTable.filter(_.user === from.user).map(_.id).result.headOption
      user <- maybeUser.map(DBIO.successful).getOrElse(DBIO.failed(new Exception(s"User not found: '${from.user}'.")))
      boatId <- boatInserts += BoatInput(from.boat, user)
    } yield boatId
  }
}
