package com.malliina.boat.auth

import com.malliina.boat.db.{IdentityException, JWTError, MissingCredentials}
import com.malliina.http.OkClient
import com.malliina.play.auth.{AuthError, IdToken, InvalidClaims, KeyClient}
import com.malliina.values.Email
import play.api.Logger
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

object GoogleTokenAuth {
  def apply(webClientId: String,
            iosClientId: String,
            http: OkClient,
            ec: ExecutionContext): GoogleTokenAuth =
    new GoogleTokenAuth(KeyClient.google(webClientId, http), KeyClient.google(iosClientId, http))(ec)

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

class GoogleTokenAuth(web: KeyClient, ios: KeyClient)(implicit ec: ExecutionContext) {
  private val log = Logger(getClass)

  def authEmail(rh: RequestHeader): Future[Email] =
    GoogleTokenAuth.readAuthToken(rh).map { token =>
      validate(IdToken(token)).flatMap { e =>
        e.fold(err => Future.failed(IdentityException(JWTError(rh, err))), email => Future.successful(email))
      }
    }.getOrElse {
      Future.failed(IdentityException(MissingCredentials(rh)))
    }

  def validate(token: IdToken): Future[Either[AuthError, Email]] =
    validate(token, web).flatMap { e =>
      e.fold(err => {
        log.info(s"Failed to validate token '$token': '${err.message}', falling back to iOS client ID validation...")
        validate(token, ios)
      }, email => {
        Future.successful(Right(email))
      })
    }

  def validate(token: IdToken, client: KeyClient): Future[Either[AuthError, Email]] =
    client.validate(token).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim("email_verified"), "email_verified").flatMap { isVerified =>
          if (isVerified) parsed.readString("email").map(Email.apply)
          else Left(InvalidClaims(token, "Email not verified."))
        }
      }
    }
}
