package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.DatabaseUserManager.log
import com.malliina.boat.{Boat, BoatInfo, BoatInput, BoatNames, BoatToken, BoatTokens, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken}
import com.malliina.values.{Email, UserId, Username}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object DatabaseUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): DatabaseUserManager =
    new DatabaseUserManager(db)(ec)

  def collect(rows: Seq[JoinedUser]) = collectUsers(rows.map(r => (r.user, r.boat)))

  def collectUsers(rows: Seq[(UserRow, Option[BoatRow])]): Vector[UserInfo] =
    rows.foldLeft(Vector.empty[UserInfo]) {
      case (acc, (user, boat)) =>
        val idx = acc.indexWhere(_.id == user.id)
        val newBoats =
          boat.toSeq.map(b => Boat(b.id, b.name, b.token, b.added.toEpochMilli))
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(boats = old.boats ++ newBoats))
        } else {
          user.email.fold(acc) { email =>
            acc :+ UserInfo(
              user.id,
              user.user,
              email,
              user.language,
              newBoats,
              user.enabled,
              user.added.toEpochMilli
            )
          }
        }
    }

  def collectBoats(rows: Seq[JoinedTrack], formatter: TimeFormatter): Seq[BoatInfo] =
    rows.foldLeft(Vector.empty[BoatInfo]) { (acc, row) =>
      val boatIdx =
        acc.indexWhere(b => b.user == row.username && b.boatId == row.boatId)
      val newRow = row.strip(formatter)
      if (boatIdx >= 0) {
        val old = acc(boatIdx)
        acc.updated(boatIdx, old.copy(tracks = old.tracks :+ newRow))
      } else {
        acc :+ BoatInfo(
          row.boatId,
          row.boatName,
          row.username,
          row.language,
          Seq(newRow)
        )
      }
    }
}

class DatabaseUserManager(val db: BoatSchema)(implicit ec: ExecutionContext) extends UserManager {

  import db._
  import db.api._

  val userInserts = usersTable.map(_.forInserts).returning(usersTable.map(_.id))

  override def userInfo(email: Email): Future[UserInfo] = {
    val action = for {
      id <- getOrCreate(email)
      info <- userAuthAction(usersTable.filter(_.id === id))
    } yield info
    db.run(action.transactionally).flatMap { e =>
      e.fold(
        err => Future.failed(IdentityException(err)),
        user => Future.successful(user)
      )
    }
  }

  def userMeta(email: Email): Future[UserRow] = action {
    first(
      usersTable.filter(u => u.email.isDefined && u.email === email),
      s"User not found: '$email'."
    )
  }

  private def getOrCreate(email: Email): DBIOAction[UserId, NoStream, Effect.All] =
    usersTable
      .filter(u => u.email.isDefined && u.email === email)
      .result
      .flatMap { rows =>
        rows.headOption.map { user =>
          DBIO.successful(user.id)
        }.getOrElse {
          for {
            userId <- userInserts += NewUser(
              Username(email.email),
              Option(email),
              UserToken.random(),
              enabled = true
            )
            _ <- boatInserts += BoatInput(
              BoatNames.random(),
              BoatTokens.random(),
              userId
            )
          } yield userId
        }
      }

  override def users: Future[Seq[UserInfo]] = action {
    userInfos(usersTable)
  }

  private def userAuthAction(filteredUsers: Query[UsersTable, UserRow, Seq]) =
    userInfos(filteredUsers).map { users =>
      users.headOption.map { profile =>
        if (profile.enabled) Right(profile)
        else Left(UserDisabled(profile.username))
      }.getOrElse {
        Left(InvalidCredentials(None))
      }
    }

  private def userInfos(filteredUsers: Query[UsersTable, UserRow, Seq]) =
    filteredUsers.joinLeft(boatsTable).on(_.id === _.owner).result.map { rows =>
      DatabaseUserManager.collectUsers(rows)
    }

  override def authBoat(token: BoatToken): Future[JoinedBoat] = action {
    boatsView.filter(_.token === token).result.headOption.flatMap { maybeBoat =>
      maybeBoat
        .map(DBIO.successful)
        .getOrElse(DBIO.failed(IdentityException(InvalidToken(token))))
    }
  }

  override def boats(email: Email): Future[UserBoats] = action {
    for {
      id <- getOrCreate(email)
      user <- first(
        usersTable.filter(_.id === id),
        s"Not found or not enabled: '$email'."
      )
      bs <- loadBoats(
        tracksViewNonEmpty.filter(r => r.user === id && r.points > 100),
        TimeFormatter(user.language)
      )
      gpss <- boatsView
        .filter(
          bv =>
            bv.email.isDefined && bv.email === email && !bv.boat
              .in(tracksTable.map(_.boat))
        )
        .result
        .map { jbs =>
          jbs.map { jb =>
            BoatInfo(jb.device, jb.boatName, jb.username, jb.language, Nil)
          }
        }
    } yield UserBoats(user.user, user.language, bs ++ gpss)
  }

  private def loadBoats(q: Query[LiftedJoinedTrack, JoinedTrack, Seq], formatter: TimeFormatter) = {
    val tracksAction = q
      .sortBy(
        r => (r.user, r.boatId, r.start.desc, r.trackAdded.desc, r.track.desc)
      )
      .result
    for {
      tracks <- tracksAction
    } yield DatabaseUserManager.collectBoats(tracks, formatter)
  }

  override def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] = {
    val action = for {
      uid <- userInserts += user
      u <- first(usersTable.filter(_.id === uid), s"User not found: '$uid'.")
    } yield u
    db.run(action).map(Right.apply).recover {
      case sqle: SQLException if sqle.getMessage contains "primary key violation" =>
        Left(AlreadyExists(user.username))
    }
  }

  override def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] =
    db.run(usersTable.filter(_.user === user).delete).map { changed =>
      if (changed > 0) {
        log.info(s"Deleted user '$user'.")
        Right(())
      } else {
        Left(UserDoesNotExist(user))
      }
    }

  def changeLanguage(user: UserId, to: Language): Future[Boolean] = action {
    usersTable.filter(_.id === user).map(_.language).update(to).map { rows =>
      val wasChanged = rows > 0
      if (wasChanged) {
        log.info(s"Changed language of user ID '$user' to '$to'.")
      }
      wasChanged
    }
  }

  private def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] =
    db.run(a)
}
