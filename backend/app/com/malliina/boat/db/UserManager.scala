package com.malliina.boat.db

import com.malliina.boat.{BoatInfo, BoatToken, JoinedBoat, UserToken}
import com.malliina.play.auth.AuthError
import com.malliina.values.{Email, Password, UserId, Username}
import org.apache.commons.codec.digest.DigestUtils
import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait UserManager {
  def authUser(token: UserToken): Future[Either[IdentityError, DataUser]]

  def authEmail(email: Email): Future[Either[IdentityError, DataUser]]

  def authBoat(token: BoatToken): Future[Either[IdentityError, JoinedBoat]]

  def boats(user: Email): Future[Seq[BoatInfo]]

  def updatePassword(user: Username, newPass: Password): Future[Unit]

  def addUser(user: Username, pass: Password): Future[Either[AlreadyExists, UserId]] =
    addUser(NewUser(user, None, hash(user, pass), UserToken.random(), enabled = true))

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]]

  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]]

  def users: Future[Seq[DataUser]]

  protected def hash(user: Username, pass: Password): String = DigestUtils.md5Hex(user.name + ":" + pass.pass)
}

sealed trait IdentityError

case class AlreadyExists(user: Username) extends IdentityError

case class InvalidCredentials(user: Option[Username] = None) extends IdentityError

case class InvalidToken(token: BoatToken) extends IdentityError

case class UserDisabled(user: Username) extends IdentityError

case class UserDoesNotExist(user: Username) extends IdentityError

case class MissingToken(rh: RequestHeader) extends IdentityError

case class MissingCredentials(rh: RequestHeader) extends IdentityError

case class JWTError(rh: RequestHeader, error: AuthError) extends IdentityError
