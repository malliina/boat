package com.malliina.boat.db

import com.malliina.boat.http.{AccessResult, InviteInfo}
import com.malliina.boat.{BoatToken, DeviceId, InviteState, JoinedBoat, Language, UserBoats, UserInfo}
import com.malliina.play.auth.AuthError
import com.malliina.values.{Email, ErrorMessage, Password, UserId, Username}
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
  def invite(i: InviteInfo): Future[AccessResult]
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): Future[AccessResult]
  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): Future[AccessResult]
  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): Future[Long]

  protected def hash(user: Username, pass: Password): String =
    DigestUtils.md5Hex(user.name + ":" + pass.pass)
}

sealed abstract class IdentityError(val message: ErrorMessage) {
  def this(message: String) = this(ErrorMessage(message))
  def toException: IdentityException = IdentityException(this)
  override def toString: String = message.message
}

case class AlreadyExists(user: Username) extends IdentityError(s"User $user already exists.")
case class InvalidCredentials(user: Option[Username] = None)
  extends IdentityError(s"Invalid credentials.")
case class InvalidToken(token: BoatToken) extends IdentityError(s"Invalid token: '$token'.")
case class UserDisabled(user: Username) extends IdentityError(s"User is disabled: '$user'.")
case class UserDoesNotExist(user: Username) extends IdentityError(s"User does not exist: '$user'.")
case class MissingToken(rh: RequestHeader) extends IdentityError(s"Missing token in '$rh'.")
case class MissingCredentials(rh: RequestHeader)
  extends IdentityError(s"Missing credentials in '$rh'.")
case class JWTError(rh: RequestHeader, error: AuthError) extends IdentityError(error.message)

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error) {
  def rh: RequestHeader = error.rh
}

class IdentityException(val error: IdentityError) extends Exception(error.message.message)

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_) => new MissingCredentialsException(mc)
    case other                      => new IdentityException(other)
  }

  def missingCredentials(rh: RequestHeader): MissingCredentialsException =
    new MissingCredentialsException(MissingCredentials(rh))
}
