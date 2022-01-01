package com.malliina.web

import cats.effect.IO
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.oauth.TokenResponse
import com.malliina.util.AppLogger
import com.malliina.values.*
import com.malliina.web.*
import com.malliina.web.AppleAuthFlow.{RefreshTokenValue, log, staticConf}
import com.malliina.web.AppleTokenValidator.appleIssuer
import com.malliina.web.OAuthKeys.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.UrlForm

import java.time.Instant
import java.time.temporal.ChronoUnit

object AppleAuthFlow:
  private val log = AppLogger(getClass)
  val emailScope = "email"
  val RefreshTokenValue = "refresh_token"
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
  val validator: AppleTokenValidator,
  http: HttpClient[IO]
) extends StaticFlowStart
  with CallbackValidator[Email]:
  override val conf: StaticConf = staticConf(authConf)

  override def validate(
    code: Code,
    redirectUrl: FullUrl,
    requestNonce: Option[String]
  ): IO[Either[AuthError, Email]] =
    val params = codeParameters(code) ++ Map(RedirectUri -> redirectUrl.url)
    http.postFormAs[TokenResponse](conf.tokenEndpoint, params).flatMap { tokens =>
      http.getAs[JWTKeys](AppleAuthFlow.jwksUri).map { keys =>
        validator.validate(tokens.id_token, keys.keys, Instant.now()).flatMap { v =>
          v.readString(EmailKey).map(Email.apply)
        }
      }
    }

  def refreshToken(code: Code): IO[RefreshTokenResponse] =
    log.info(s"Exchanging authorization code for tokens...")
    val params = codeParameters(code)
    http.postFormAs[RefreshTokenResponse](conf.tokenEndpoint, params)

  def verifyRefreshToken(token: RefreshToken): IO[TokenResponse] =
    val params = commonParameters(RefreshTokenValue) ++ Map(
      GrantType -> RefreshTokenValue,
      RefreshTokenValue -> token.value
    )
    http.postFormAs[TokenResponse](conf.tokenEndpoint, params)

  override def extraRedirParams(redirectUrl: FullUrl): Map[String, String] =
    Map(ResponseType -> CodeKey, "response_mode" -> "form_post")

  private def codeParameters(code: Code) =
    commonParameters(AuthorizationCode) ++ Map(CodeKey -> code.code)

  private def commonParameters(grantType: String) = Map(
    ClientIdKey -> authConf.clientId.value,
    ClientSecretKey -> authConf.clientSecret.value,
    GrantType -> grantType
  )
