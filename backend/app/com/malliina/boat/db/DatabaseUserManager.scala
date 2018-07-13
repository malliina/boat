package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.DatabaseUserManager.log
import com.malliina.boat.{BoatInfo, BoatToken, Earth, JoinedTrack, TrackId, User, UserEmail, UserId, UserToken}
import com.malliina.measure.Distance
import com.malliina.play.models.Password
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object DatabaseUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): DatabaseUserManager =
    new DatabaseUserManager(db)(ec)
}

class DatabaseUserManager(val db: BoatSchema)(implicit ec: ExecutionContext)
  extends UserManager {

  import db.api._
  import db._

  /**
    *
    * @param user username
    * @param pass password
    * @return true if the credentials are valid, false otherwise
    */
  override def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = {
    val passHash = hash(user, pass)
    withUserAuth(usersTable.filter(u => u.user === user && u.passHash === passHash))
  }

  override def authUser(token: UserToken): Future[Either[IdentityError, DataUser]] =
    withUserAuth(usersTable.filter(_.token === token))

  override def authEmail(email: UserEmail): Future[Either[IdentityError, DataUser]] = {
    withUserAuth(usersTable.filter(u => u.email.isDefined && u.email === email))
  }

  private def withUserAuth(filteredUsers: Query[UsersTable, DataUser, Seq]) = action {
    filteredUsers.result.headOption.map { maybeUser =>
      maybeUser.map { profile =>
        if (profile.enabled) Right(profile)
        else Left(UserDisabled(profile.username))
      }.getOrElse {
        Left(InvalidCredentials(None))
      }
    }
  }

  override def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = action {
    loadBoats(tracksView.filter(r => r.boatToken === token))
      .map(bs => bs.headOption.toRight(InvalidToken(token)))
  }

  override def boats(email: UserEmail): Future[Seq[BoatInfo]] = action {
    loadBoats(tracksView.filter(r => r.email.isDefined && r.email === email && r.points > 100))
  }

  private def loadBoats(q: Query[LiftedJoinedTrack, JoinedTrack, Seq]) = {
    val tracksAction = q
      .sortBy(r => (r.user, r.boat, r.start.desc, r.trackAdded.desc, r.track.desc))
      .result
    for {
      tracks <- tracksAction
      distances <- DBIO.sequence(tracks.map(t => distance(t.track).map(d => t.track -> d)))
    } yield collectBoats(tracks, distances.toMap)
  }

  private def distance(track: TrackId) = pointsTable.filter(_.track === track).result.map { coords =>
    Earth.length(coords.map(_.toCoord).toList)
  }

  private def collectBoats(rows: Seq[JoinedTrack], distances: Map[TrackId, Distance]): Seq[BoatInfo] =
    rows.foldLeft(Vector.empty[BoatInfo]) { (acc, row) =>
      val boatIdx = acc.indexWhere(b => b.user == row.username && b.boatId == row.boat)
      val newRow = row.strip(distances.getOrElse(row.track, Distance.zero))
      if (boatIdx >= 0) {
        val old = acc(boatIdx)
        acc.updated(boatIdx, old.copy(tracks = old.tracks :+ newRow))
      } else {
        acc :+ BoatInfo(row.boat, row.boatName, row.username, Seq(newRow))
      }
    }

  override def updatePassword(user: User, newPass: Password): Future[Unit] = {
    val action = usersTable
      .filter(u => u.user === user)
      .map(_.passHash)
      .update(hash(user, newPass))
    db.run(action).map { changed =>
      if (changed > 0) log.info(s"Changed password for '$user'.")
      else log.warn(s"Failed to change password for '$user'.")
    }
  }

  override def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]] = {
    val action = usersTable.map(_.forInserts).returning(usersTable.map(_.id)) += user
    db.run(action).map(id => Right(id)).recover {
      case sqle: SQLException if sqle.getMessage contains "primary key violation" =>
        Left(AlreadyExists(user.username))
    }
  }

  override def deleteUser(user: User): Future[Either[UserDoesNotExist, Unit]] =
    db.run(usersTable.filter(_.user === user).delete).map { changed =>
      if (changed > 0) {
        log.info(s"Deleted user '$user'.")
        Right(())
      } else {
        Left(UserDoesNotExist(user))
      }
    }

  override def users: Future[Seq[DataUser]] = action {
    usersTable.result
  }

  private def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)
}
