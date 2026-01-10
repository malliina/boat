package com.malliina.boat.http4s

import com.malliina.boat.auth.{BasicCredentials, EmailAuth}
import com.malliina.boat.db.{CustomJwt, MissingCredentials}
import com.malliina.values.Literals.err
import com.malliina.values.{IdToken, Password, Username}
import com.malliina.web.{AppleAuthFlow, EmailAuthFlow, GoogleAuthFlow}
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Headers}

object Auth:
  val noCredentials = err"No credentials."

  def basic(hs: Headers): Either[MissingCredentials, BasicCredentials] = {
    headerCredentials(hs).flatMap:
      case org.http4s.BasicCredentials(user, pass) =>
        val parsed = for
          username <- Username.build(user)
          password <- Password.build(pass)
        yield BasicCredentials(username, password)
        parsed.left.map(err => MissingCredentials(err.message, hs))
      case _ =>
        Left(MissingCredentials("Username and password auth expected.", hs))
  }

  def token(hs: Headers): Either[MissingCredentials, IdToken] =
    headerCredentials(hs).flatMap:
      case Token(_, token) =>
        IdToken.build(token).left.map(err => MissingCredentials(err.message, hs))
      case _ =>
        Left(MissingCredentials("Basic auth expected.", hs))

  private def headerCredentials(hs: Headers): Either[MissingCredentials, Credentials] = hs
    .get[Authorization]
    .map(h => h.credentials)
    .toRight(MissingCredentials(noCredentials, hs))

case class AuthComps[F[_]](
  google: EmailAuth[F],
  web: Http4sAuth[F],
  googleFlow: GoogleAuthFlow[F],
  microsoftBoatFlow: EmailAuthFlow[F],
  microsoftCarFlow: EmailAuthFlow[F],
  appleWebFlow: AppleAuthFlow[F],
  appleAppFlow: AppleAuthFlow[F],
  customJwt: CustomJwt
)
