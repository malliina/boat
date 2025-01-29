package com.malliina.web

import cats.effect.Sync
import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import com.malliina.http.HttpClient
import com.malliina.values.Literals.err
import com.malliina.values.{Email, TokenValue}
import com.malliina.web.AppleTokenValidator.EmailVerified
import com.malliina.web.OAuthKeys.EmailKey

import java.time.Instant

object AppleTokenValidator:
  val EmailVerified = "email_verified"
  val appleIssuer = Issuer("https://appleid.apple.com")
  // aud for tokens obtained in the iOS app SIWA flow
  val boatClientId = ClientId("com.malliina.BoatTracker")

  def app[F[_]: Sync](http: HttpClient[F]): AppleTokenValidator[F] =
    AppleTokenValidator(Seq(boatClientId), http)

class AppleTokenValidator[F[_]: Sync](
  clientIds: Seq[ClientId],
  http: HttpClient[F],
  issuers: Seq[Issuer] = Seq(appleIssuer)
) extends TokenVerifier[F](issuers):
  val F = Sync[F]

  def validateOrFail(token: TokenValue, now: Instant): F[Email] =
    extractEmail(token, now).flatMap: e =>
      e.fold(err => F.raiseError(AuthException(err)), F.pure)

  def extractEmail(token: TokenValue, now: Instant): F[Either[AuthError, Email]] =
    validateToken(token, now).map: outcome =>
      for
        v <- outcome
        email <- v.readString(EmailKey).map(Email.apply)
        emailVerified <- v.readBoolean(EmailVerified)
        result <-
          if emailVerified then Right(email)
          else Left(InvalidClaims(token, err"Email not verified."))
      yield result

  def validateToken(
    token: TokenValue,
    now: Instant
  ): F[Either[AuthError, Verified]] =
    http.getAs[JWTKeys](AppleAuthFlow.jwksUri).map(keys => validate(token, keys.keys, now))

  override protected def validateClaims(
    parsed: ParsedJWT,
    now: Instant
  ): Either[JWTError, ParsedJWT] =
    checkContains(Aud, clientIds.map(_.value), parsed).map(_ => parsed)
