package com.malliina.boat.db

import java.sql.SQLException
import java.time.Instant

import com.malliina.boat.db.DatabaseUserManager.log
import com.malliina.boat.{BoatInfo, BoatToken, Earth, JoinedTrack, TrackId, User, UserEmail, UserId}
import com.malliina.measure.Distance
import com.malliina.play.models.Password
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

object DatabaseUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): DatabaseUserManager =
    new DatabaseUserManager(db)(ec)
}

class DatabaseUserManager(val db: BoatSchema)(implicit ec: ExecutionContext)
  extends UserManager {

  import db.{usersTable, tracksView, pointsTable, LiftedJoinedTrack}

  import db.impl.api._
  import db.mappings._

  /**
    *
    * @param user username
    * @param pass password
    * @return true if the credentials are valid, false otherwise
    */
  override def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = action {
    val passHash = hash(user, pass)
    val users = usersTable.filter(u => u.user === user && u.passHash === passHash)
    users.result.headOption.map { maybeUser =>
      maybeUser.map { profile =>
        if (profile.enabled) Right(profile)
        else Left(UserDisabled(profile.username))
      }.getOrElse {
        Left(InvalidCredentials(user))
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
      if (boatIdx >= 0) {
        val old = acc(boatIdx)
        acc.updated(boatIdx, old.copy(tracks = old.tracks :+ row.strip(distances.getOrElse(row.track, Distance.zero))))
      } else {
        acc :+ BoatInfo(row.boat, row.boatName, row.username, Seq(row.strip(distances.getOrElse(row.track, Distance.zero))))
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

object PassThroughUserManager extends UserManager {
  val god = DataUser(UserId(1L), User("test"), None, "", enabled = true, added = Instant.now())

  def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = fut(Left(InvalidToken(token)))

  def boats(user: UserEmail): Future[Seq[BoatInfo]] = fut(Nil)

  def updatePassword(user: User, newPass: Password): Future[Unit] = fut(())

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]] = fut(Left(AlreadyExists(user.username)))

  def deleteUser(user: User): Future[Either[UserDoesNotExist, Unit]] = fut(Left(UserDoesNotExist(user)))

  def users: Future[Seq[DataUser]] = fut(Seq(god))

  def fut[T](t: T) = Future.successful(t)
}

trait UserManager {
  /**
    *
    * @param user username
    * @param pass password
    * @return true if the credentials are valid, false otherwise
    */
  def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]]

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]]

  def boats(user: UserEmail): Future[Seq[BoatInfo]]

  def updatePassword(user: User, newPass: Password): Future[Unit]

  def addUser(user: User, pass: Password): Future[Either[AlreadyExists, UserId]] =
    addUser(NewUser(user, None, hash(user, pass), enabled = true))

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]]

  def deleteUser(user: User): Future[Either[UserDoesNotExist, Unit]]

  def users: Future[Seq[DataUser]]

  protected def hash(user: User, pass: Password): String = DigestUtils.md5Hex(user.name + ":" + pass.pass)
}

sealed trait IdentityError

case class AlreadyExists(user: User) extends IdentityError

case class InvalidCredentials(user: User) extends IdentityError

case class InvalidToken(token: BoatToken) extends IdentityError

case class UserDisabled(user: User) extends IdentityError

case class UserDoesNotExist(user: User) extends IdentityError

case class MissingToken(rh: RequestHeader) extends IdentityError

case class MissingCredentials(rh: RequestHeader) extends IdentityError
