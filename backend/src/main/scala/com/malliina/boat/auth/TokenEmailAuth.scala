package com.malliina.boat.auth

import cats.effect.IO
import com.malliina.boat.db.{IdentityException, JWTError, MissingCredentials}
import com.malliina.boat.http4s.Auth
import com.malliina.http.HttpClient
import com.malliina.values.{Email, ErrorMessage, IdToken}
import com.malliina.web._
import org.http4s.Headers

import java.time.Instant

object TokenEmailAuth {

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId android apps
    * @param iosClientId ios apps
    */
  def apply(
    webClientId: ClientId,
    iosClientId: ClientId,
    microsoftClientId: ClientId,
    http: HttpClient[IO]
  ): TokenEmailAuth = {
    val google = GoogleAuthFlow.keyClient(Seq(webClientId, iosClientId), http)
    val microsoft = MicrosoftAuthFlow.keyClient(Seq(microsoftClientId), http)
    new TokenEmailAuth(google, microsoft)
  }
}

/** Validates Google ID tokens and extracts the email address.
  */
class TokenEmailAuth(google: KeyClient, microsoft: KeyClient) extends EmailAuth {
  val EmailKey = "email"
  val EmailVerified = "email_verified"

  def authEmail(headers: Headers): IO[Email] =
    Auth
      .token(headers)
      .map { token =>
        validateAny(token, Instant.now()).flatMap { e =>
          e.fold(
            err => IO.raiseError(IdentityException(JWTError(err, headers))),
            email => IO.pure(email)
          )
        }
      }
      .getOrElse {
        IO.raiseError(IdentityException(MissingCredentials(headers)))
      }

  private def validateAny(token: IdToken, now: Instant) =
    validateGoogle(token, now).flatMap { e =>
      e.fold(
        err => validateMicrosoft(token, now),
        ok => IO.pure(Right(ok))
      )
    }

  private def validateGoogle(token: IdToken, now: Instant): IO[Either[AuthError, Email]] =
    google.validate(token, now).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified).flatMap {
          isVerified =>
            if (isVerified) parsed.readString(EmailKey).map(Email.apply)
            else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
        }
      }
    }

  private def validateMicrosoft(token: IdToken, now: Instant) =
    microsoft.validate(token, now).map { outcome =>
      outcome.flatMap { v =>
        v.readString(EmailKey).map(Email.apply)
      }
    }
}
