package com.malliina.boat.auth

import com.malliina.boat.UserEmail
import com.malliina.boat.db.{IdentityError, JWTError}
import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthError, IdToken, InvalidClaims, KeyClient}
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

object GoogleTokenAuth {
  def apply(clientId: String, http: OkClient, ec: ExecutionContext): GoogleTokenAuth =
    new GoogleTokenAuth(KeyClient.google(clientId, http))(ec)

  def readAuthToken(rh: RequestHeader, scheme: String = "Bearer"): Option[String] =
    rh.headers.get(AUTHORIZATION).flatMap { authInfo =>
      authInfo.split(" ") match {
        case Array(name, value) if name.toLowerCase == scheme.toLowerCase =>
          Option(value)
        case _ =>
          None
      }
    }
}

class GoogleTokenAuth(client: KeyClient)(implicit ec: ExecutionContext) {
  def auth(rh: RequestHeader): Option[Future[Either[IdentityError, UserEmail]]] =
    GoogleTokenAuth.readAuthToken(rh)
      .map(token => validate(IdToken(token)).map(_.left.map(err => JWTError(rh, err))))

  def validate(token: IdToken): Future[Either[AuthError, UserEmail]] =
    client.validate(token).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        for {
          _ <- parsed.read(parsed.claims.getBooleanClaim("email_verified"), "email_verified").filterOrElse(_ == true, InvalidClaims(token, "Email not verified."))
          email <- v.parsed.readString("email").map(UserEmail.apply)
        } yield email
      }
    }
}