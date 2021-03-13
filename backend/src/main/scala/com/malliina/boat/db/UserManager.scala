package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.http.{AccessResult, InviteInfo}
import com.malliina.boat.{BoatToken, DeviceId, InviteState, JoinedBoat, Language, UserBoats, UserInfo, Usernames}
import com.malliina.values._
import com.malliina.web.AuthError
import org.apache.commons.codec.digest.DigestUtils
import org.http4s.Headers

trait UserManager {
  def userMeta(email: Email): IO[UserRow]

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
  def userInfo(email: Email): IO[UserInfo]
  def authBoat(token: BoatToken): IO[JoinedBoat]
  def boats(user: Email): IO[UserBoats]
  def addUser(user: NewUser): IO[Either[AlreadyExists, UserRow]]
  def deleteUser(user: Username): IO[Either[UserDoesNotExist, Unit]]
  def initUser(user: Username = Usernames.anon): IO[NewUser]
  def changeLanguage(user: UserId, to: Language): IO[Boolean]
  def invite(i: InviteInfo): IO[AccessResult]
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): IO[AccessResult]
  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): IO[AccessResult]
  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): IO[Long]

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
case class MissingToken(hs: Headers) extends IdentityError(s"Missing token in '$hs'.")
case class MissingCredentials(hs: Headers) extends IdentityError(s"Missing credentials in '$hs'.")
case class JWTError(hs: Headers, error: AuthError) extends IdentityError(error.message)

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error) {
  def headers = error.hs
}

class IdentityException(val error: IdentityError) extends Exception(error.message.message)

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_) => new MissingCredentialsException(mc)
    case other                      => new IdentityException(other)
  }

  def missingCredentials(hs: Headers): MissingCredentialsException =
    new MissingCredentialsException(MissingCredentials(hs))
}
