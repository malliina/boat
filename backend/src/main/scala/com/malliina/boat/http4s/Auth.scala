package com.malliina.boat.http4s

import com.malliina.boat.auth.{BasicCredentials, GoogleTokenAuth}
import com.malliina.boat.db.MissingCredentials
import com.malliina.values.{IdToken, Password, Username}
import com.malliina.web.GoogleAuthFlow
import org.http4s.Credentials.Token
import org.http4s.{Credentials, Headers}
import org.http4s.headers.Authorization

object Auth {
  def basic(hs: Headers): Either[MissingCredentials, BasicCredentials] =
    headerCredentials(hs).flatMap {
      case org.http4s.BasicCredentials(user, pass) =>
        Right(BasicCredentials(Username(user), Password(pass)))
      case _ =>
        Left(MissingCredentials("Username and password auth expected.", hs))
    }

  def token(hs: Headers): Either[MissingCredentials, IdToken] =
    headerCredentials(hs).flatMap {
      case Token(scheme, token) =>
        Right(IdToken(token))
      case _ =>
        Left(MissingCredentials("Basic auth expected.", hs))
    }

  def headerCredentials(hs: Headers): Either[MissingCredentials, Credentials] = hs
    .get(Authorization)
    .map(h => h.credentials)
    .toRight(MissingCredentials("No credentials.", hs))
}

case class AuthComps(google: GoogleTokenAuth, web: Http4sAuth, flow: GoogleAuthFlow)
