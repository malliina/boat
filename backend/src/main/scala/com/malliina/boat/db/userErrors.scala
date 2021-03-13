package com.malliina.boat.db

import com.malliina.boat.BoatToken
import com.malliina.values.{ErrorMessage, Username}
import com.malliina.web.AuthError
import org.http4s.Headers

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
case class MissingCredentials(msg: ErrorMessage, hs: Headers) extends IdentityError(msg)
object MissingCredentials {
  def apply(msg: String, hs: Headers): MissingCredentials =
    MissingCredentials(ErrorMessage(msg), hs)
  def apply(hs: Headers): MissingCredentials = apply(s"Missing credentials in '$hs'.", hs)
}
case class JWTError(error: AuthError, hs: Headers) extends IdentityError(error.message)

class MissingCredentialsException(error: MissingCredentials) extends IdentityException(error) {
  def headers = error.hs
}

class IdentityException(val error: IdentityError) extends Exception(error.message.message)

object IdentityException {
  def apply(error: IdentityError): IdentityException = error match {
    case mc @ MissingCredentials(_, _) => new MissingCredentialsException(mc)
    case other                         => new IdentityException(other)
  }

  def missingCredentials(hs: Headers): MissingCredentialsException =
    new MissingCredentialsException(MissingCredentials(hs))
}
