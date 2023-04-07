package com.malliina.boat.auth

import cats.effect.{IO, Sync}
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.boat.{Errors, SingleError}
import com.malliina.boat.auth.TokenEmailAuth.log
import com.malliina.boat.db.{CustomJwt, IdentityException, JWTError, MissingCredentials}
import com.malliina.boat.http4s.Auth
import com.malliina.http.HttpClient
import com.malliina.util.AppLogger
import com.malliina.values.{Email, ErrorMessage, IdToken}
import com.malliina.web.*
import org.http4s.Headers

import java.time.Instant

object TokenEmailAuth:
  private val log = AppLogger(getClass)

  /** The Android app uses the web client ID while the iOS app uses the iOS client ID.
    *
    * @param webClientId
    *   android apps
    * @param iosClientId
    *   ios apps
    */
  def default[F[_]: Sync](
    webClientId: ClientId,
    iosClientId: ClientId,
    microsoftClientId: ClientId,
    http: HttpClient[F],
    custom: CustomJwt
  ): TokenEmailAuth[F] =
    val google = GoogleAuthFlow.keyClient(Seq(webClientId, iosClientId), http)
    val microsoft = MicrosoftAuthFlow.keyClient(Seq(microsoftClientId), http)
    TokenEmailAuth(google, microsoft, custom)

/** Validates tokens and extracts the email address.
  */
class TokenEmailAuth[F[_]: Sync](google: KeyClient[F], microsoft: KeyClient[F], custom: CustomJwt)
  extends EmailAuth[F]:
  val EmailKey = "email"
  val EmailVerified = "email_verified"
  val F = Sync[F]

  def authEmail(headers: Headers, now: Instant): F[Email] =
    Auth
      .token(headers)
      .map { token =>
        validateAny(token, now).flatMap { e =>
          e.fold(
            err =>
              val ex = WebAuthException(err, headers)
              log.warn(s"Token failed validation. $err", ex)
              F.raiseError(ex)
            ,
            email => F.pure(email)
          )
        }
      }
      .getOrElse {
        F.raiseError(IdentityException(MissingCredentials(headers)))
      }

  private def validateAny(token: IdToken, now: Instant): F[Either[AuthError, Email]] =
    validateGoogle(token, now).flatMap { e =>
      e.fold(
        err =>
          validateMicrosoft(token, now).flatMap { e2 =>
            e2.fold(err => F.pure(custom.email(token, now)), ok => F.pure(Right(ok)))
          },
        ok => F.pure(Right(ok))
      )
    }

  private def validateGoogle(token: IdToken, now: Instant): F[Either[AuthError, Email]] =
    google.validate(token, now).map { outcome =>
      outcome.flatMap { v =>
        val parsed = v.parsed
        parsed.read(parsed.claims.getBooleanClaim(EmailVerified), EmailVerified).flatMap {
          isVerified =>
            if isVerified then parsed.readString(EmailKey).map(Email.apply)
            else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
        }
      }
    }

  private def validateMicrosoft(token: IdToken, now: Instant): F[Either[AuthError, Email]] =
    microsoft.validate(token, now).map { outcome =>
      outcome.flatMap { v =>
        v.readString(EmailKey).map(Email.apply)
      }
    }
