package com.malliina.boat.auth

import cats.effect.IO
import com.malliina.boat.db.{IdentityException, JWTError, MissingCredentials}
import com.malliina.boat.http4s.Auth
import com.malliina.http.HttpClient
import com.malliina.play.auth.KeyClient
import com.malliina.values.{Email, ErrorMessage, IdToken}
import com.malliina.web.{AuthError, ClientId, InvalidClaims, KeyClient}
import org.http4s.{Headers, Request}

object GoogleTokenAuth {

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId android apps
    * @param iosClientId ios apps
    */
  def apply(
    webClientId: ClientId,
    iosClientId: ClientId,
    http: HttpClient[IO]
  ): GoogleTokenAuth =
    new GoogleTokenAuth(KeyClient.google(Seq(webClientId, iosClientId), http))
}

/** Validates Google ID tokens and extracts the email address.
  */
class GoogleTokenAuth(validator: KeyClient) extends EmailAuth {
  val EmailKey = "email"
  val EmailVerified = "email_verified"

  def authEmail(headers: Headers): IO[Email] =
    Auth
      .token(headers)
      .map { token =>
        validate(token).flatMap { e =>
          e.fold(
            err => IO.raiseError(IdentityException(JWTError(headers, err))),
            email => IO.pure(email)
          )
        }
      }
      .getOrElse {
        IO.raiseError(IdentityException(MissingCredentials(headers)))
      }

  private def validate(token: IdToken): IO[Either[AuthError, Email]] =
    validator.validate(token).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified).flatMap {
          isVerified =>
            if (isVerified) parsed.readString(EmailKey).map(Email.apply)
            else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
        }
      }
    }
}
