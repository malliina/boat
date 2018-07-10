package com.malliina.boat.db

import com.malliina.boat.{BoatInfo, BoatToken, User, UserEmail, UserId, UserToken}
import com.malliina.play.models.Password
import org.apache.commons.codec.digest.DigestUtils
import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait UserManager {
  /**
    *
    * @param user username
    * @param pass password
    * @return true if the credentials are valid, false otherwise
    */
  def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]]

  def authUser(token: UserToken): Future[Either[IdentityError, DataUser]]

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]]

  def boats(user: UserEmail): Future[Seq[BoatInfo]]

  def updatePassword(user: User, newPass: Password): Future[Unit]

  def addUser(user: User, pass: Password): Future[Either[AlreadyExists, UserId]] =
    addUser(NewUser(user, None, hash(user, pass), UserToken.random(), enabled = true))

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]]

  def deleteUser(user: User): Future[Either[UserDoesNotExist, Unit]]

  def users: Future[Seq[DataUser]]

  protected def hash(user: User, pass: Password): String = DigestUtils.md5Hex(user.name + ":" + pass.pass)
}

sealed trait IdentityError

case class AlreadyExists(user: User) extends IdentityError

case class InvalidCredentials(user: Option[User] = None) extends IdentityError

case class InvalidToken(token: BoatToken) extends IdentityError

case class UserDisabled(user: User) extends IdentityError

case class UserDoesNotExist(user: User) extends IdentityError

case class MissingToken(rh: RequestHeader) extends IdentityError

case class MissingCredentials(rh: RequestHeader) extends IdentityError
