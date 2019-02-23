package com.malliina.boat.auth

import com.malliina.boat.db.{IdentityException, JWTError, MissingCredentials}
import com.malliina.http.OkClient
import com.malliina.play.auth.{Auth, AuthError, IdToken, InvalidClaims, KeyClient}
import com.malliina.values.Email
import play.api.Logger
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

object GoogleTokenAuth {
  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId android apps
    * @param iosClientId ios apps
    */
  def apply(webClientId: String,
            iosClientId: String,
            http: OkClient,
            ec: ExecutionContext): GoogleTokenAuth =
    new GoogleTokenAuth(KeyClient.google(Seq(webClientId, iosClientId), http))(ec)
}

/** Validates Google ID tokens and extracts the email address.
  */
class GoogleTokenAuth(validator: KeyClient)(implicit ec: ExecutionContext) extends EmailAuth {
  val EmailKey = "email"
  val EmailVerified = "email_verified"

  def authEmail(rh: RequestHeader): Future[Email] =
    Auth
      .readAuthToken(rh)
      .map { token =>
        validate(IdToken(token)).flatMap { e =>
          e.fold(err => Future.failed(IdentityException(JWTError(rh, err))),
                 email => Future.successful(email))
        }
      }
      .getOrElse {
        Future.failed(IdentityException(MissingCredentials(rh)))
      }

  private def validate(token: IdToken): Future[Either[AuthError, Email]] =
    validator.validate(token).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified).flatMap {
          isVerified =>
            if (isVerified) parsed.readString(EmailKey).map(Email.apply)
            else Left(InvalidClaims(token, "Email not verified."))
        }
      }
    }
}
