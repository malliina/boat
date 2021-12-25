package com.malliina.web

import cats.effect.IO
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.oauth.TokenResponse
import com.malliina.values.{Email, ErrorMessage, IdToken, TokenValue}
import com.malliina.web.*
import com.malliina.web.AppleAuthFlow.staticConf
import com.malliina.web.AppleTokenValidator.appleIssuer
import com.malliina.web.OAuthKeys.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.http4s.UrlForm

import java.time.Instant
import java.time.temporal.ChronoUnit

case class AppleResponse(code: Code, state: String)

object AppleResponse:
  def apply(form: UrlForm): Either[ErrorMessage, AppleResponse] =
    def read(key: String) = form.getFirst(key).toRight(ErrorMessage(s"Not found: '$key' in $form."))
    for
      code <- read(CodeKey).map(Code.apply)
      state <- read(State)
    yield AppleResponse(code, state)

object AppleTokenValidator:
  val appleIssuer = Issuer("https://appleid.apple.com")

class AppleTokenValidator(
  clientIds: Seq[ClientId],
  http: HttpClient[IO],
  issuers: Seq[Issuer] = Seq(appleIssuer)
) extends TokenVerifier(issuers):
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

object AppleAuthFlow:
  val emailScope = "email"
  val host = FullUrl.host("appleid.apple.com")

  val authUrl = host / "/auth/authorize"
  val jwksUri = host / "/auth/keys"
  val tokensUrl = host / "/auth/token"

  def staticConf(conf: AuthConf): StaticConf = StaticConf(emailScope, authUrl, tokensUrl, conf)

/** @see
  *   https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms
  */
class AppleAuthFlow(
  authConf: AuthConf,
  validator: TokenVerifier,
  http: HttpClient[IO]
) extends StaticFlowStart
  with CallbackValidator[Email]:
  override val conf: StaticConf = staticConf(authConf)

  override def validate(
    code: Code,
    redirectUrl: FullUrl,
    requestNonce: Option[String]
  ): IO[Either[AuthError, Email]] =
    val params = tokenParameters(code, redirectUrl)
    http.postFormAs[TokenResponse](conf.tokenEndpoint, params).flatMap { tokens =>
      http.getAs[JWTKeys](AppleAuthFlow.jwksUri).map { keys =>
        validator.validate(tokens.id_token, keys.keys, Instant.now()).flatMap { v =>
          v.readString(EmailKey).map(Email.apply)
        }
      }
    }

  override def extraRedirParams(redirectUrl: FullUrl): Map[String, String] =
    Map(ResponseType -> CodeKey, "response_mode" -> "form_post")

  def tokenParameters(code: Code, redirUrl: FullUrl) = Map(
    ClientIdKey -> authConf.clientId.value,
    ClientSecretKey -> authConf.clientSecret.value,
    GrantType -> AuthorizationCode,
    CodeKey -> code.code,
    RedirectUri -> redirUrl.url
  )
