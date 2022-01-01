package com.malliina.web

import cats.effect.IO
import com.malliina.http.HttpClient
import com.malliina.values.{Email, ErrorMessage, IdToken, TokenValue}
import com.malliina.web.AppleTokenValidator.EmailVerified
import com.malliina.web.OAuthKeys.EmailKey

import java.time.Instant

object AppleTokenValidator:
  val EmailVerified = "email_verified"
  val appleIssuer = Issuer("https://appleid.apple.com")
  // aud for tokens obtained in the iOS app SIWA flow
  val boatClientId = ClientId("com.malliina.BoatTracker")
  def app(http: HttpClient[IO]): AppleTokenValidator = AppleTokenValidator(Seq(boatClientId), http)

class AppleTokenValidator(
  clientIds: Seq[ClientId],
  http: HttpClient[IO],
  issuers: Seq[Issuer] = Seq(appleIssuer)
) extends TokenVerifier(issuers):

  def validateOrFail(token: TokenValue, now: Instant): IO[Email] =
    extractEmail(token, now).flatMap { e =>
      e.fold(err => IO.raiseError(AuthException(err)), IO.pure)
    }

  def extractEmail(token: TokenValue, now: Instant): IO[Either[AuthError, Email]] =
    validateToken(token, now).map { outcome =>
      for
        v <- outcome
        email <- v.readString(EmailKey).map(Email.apply)
        emailVerified <- v.readString(EmailVerified)
        result <-
          if emailVerified.toLowerCase == "true" then Right(email)
          else Left(InvalidClaims(token, ErrorMessage("Email not verified.")))
      yield result
    }

  def validateToken(
    token: TokenValue,
    now: Instant
  ): IO[Either[AuthError, Verified]] =
    http.getAs[JWTKeys](AppleAuthFlow.jwksUri).map { keys =>
      validate(token, keys.keys, now)
    }

  override protected def validateClaims(
    parsed: ParsedJWT,
    now: Instant
  ): Either[JWTError, ParsedJWT] =
    checkContains(Aud, clientIds.map(_.value), parsed).map { _ =>
      parsed
    }
