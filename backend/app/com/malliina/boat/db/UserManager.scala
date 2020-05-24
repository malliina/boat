package com.malliina.boat.db

import com.malliina.boat.http.AccessResult
import com.malliina.boat.{BoatToken, DeviceId, InviteState, JoinedBoat, Language, UserBoats, UserInfo}
import com.malliina.play.auth.AuthError
import com.malliina.values.{Email, Password, UserId, Username}
import org.apache.commons.codec.digest.DigestUtils
import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait UserManager {
  def userMeta(email: Email): Future[UserRow]

  /** Retrieves user information for the user with the given email address. If the user does not exist, one is created
    * with the email address as the username, and with a newly created randomly named boat. This enables user login
    * without an explicit signup step.
    *
    * The email address is expected to be in possession of the user, meaning we have extracted it from a validated
    * Google ID token when calling this method.
    *
    * @param email email address of the user
    * @return user info for `email`
    */
  def userInfo(email: Email): Future[UserInfo]
  def authBoat(token: BoatToken): Future[JoinedBoat]
  def boats(user: Email): Future[UserBoats]
  def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]]
  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]]
  def users: Future[Seq[UserInfo]]
  def changeLanguage(user: UserId, to: Language): Future[Boolean]
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): Future[AccessResult]
  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): Future[AccessResult]
  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): Future[Long]

  protected def hash(user: Username, pass: Password): String =
    DigestUtils.md5Hex(user.name + ":" + pass.pass)
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

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error) {
  def rh = error.rh
}

class IdentityException(val error: IdentityError) extends Exception

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_) => new MissingCredentialsException(mc)
    case other                      => new IdentityException(other)
  }

  def missingCredentials(rh: RequestHeader): MissingCredentialsException =
    new MissingCredentialsException(MissingCredentials(rh))
}
