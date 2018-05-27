package com.malliina.boat.db

import java.sql.SQLException
import java.time.Instant

import com.malliina.boat.db.DatabaseUserManager.log
import com.malliina.boat.{BoatInfo, BoatToken, User, UserId}
import com.malliina.play.models.Password
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object DatabaseUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatSchema, ec: ExecutionContext): DatabaseUserManager =
    new DatabaseUserManager(db)(ec)
}

class DatabaseUserManager(val db: BoatSchema)(implicit ec: ExecutionContext) extends UserManager {
  val usersTable = db.usersTable
  val boatsTable = db.boatsTable

  import db.JoinedBoatShape
  import db.impl.api._
  import db.mappings._

  /**
    *
    * @param user username
    * @param pass password
    * @return true if the credentials are valid, false otherwise
    */
  override def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = {
    val passHash = hash(user, pass)
    val users = usersTable.filter(u => u.user === user && u.passHash === passHash)
    val action = users.result.headOption.map { maybeUser =>
      maybeUser.map { profile =>
        if (profile.enabled) Right(profile)
        else Left(UserDisabled(profile.username))
      }.getOrElse {
        Left(InvalidCredentials(user))
      }
    }
    db.run(action)
  }

  override def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = {
    val query = boatsTable.filter(_.token === token).join(usersTable).on(_.owner === _.id)
      .map { case (b, u) => db.LiftedJoinedBoat(b.id, b.name, u.id, u.user) }
    val action = query.result.headOption.map { maybeBoat =>
      maybeBoat.map(l => BoatInfo(l.boat, l.boatName, l.username)).toRight(InvalidToken(token))
    }
    db.run(action)
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

  override def users: Future[Seq[DataUser]] = db.run(usersTable.result)
}

object PassThroughUserManager extends UserManager {
  val god = DataUser(UserId(1L), User("test"), "", enabled = true, added = Instant.now())

  def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = fut(Left(InvalidToken(token)))

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

  def updatePassword(user: User, newPass: Password): Future[Unit]

  def addUser(user: User, pass: Password): Future[Either[AlreadyExists, UserId]] =
    addUser(NewUser(user, hash(user, pass), enabled = true))

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
