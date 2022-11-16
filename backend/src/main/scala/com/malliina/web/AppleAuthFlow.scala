package com.malliina.web

import cats.effect.IO
import com.malliina.http.{FullUrl, HttpClient}
import com.malliina.oauth.TokenResponse
import com.malliina.util.AppLogger
import com.malliina.values.*
import com.malliina.web.*
import com.malliina.web.AppleAuthFlow.{RefreshTokenValue, log, revokeUrl, staticConf}
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
  val baseUrl = host / "auth"
  val authUrl = baseUrl / "authorize"
  val jwksUri = baseUrl / "keys"
  val tokensUrl = baseUrl / "token"
  val revokeUrl = baseUrl / "revoke"

  def staticConf(conf: AuthConf): StaticConf = StaticConf(emailScope, authUrl, tokensUrl, conf)

/** @see
  *   https://developer.apple.com/documentation/signinwithapplejs/incorporating_sign_in_with_apple_into_other_platforms
  */
class AppleAuthFlow(
  authConf: AuthConf,
  val validator: AppleTokenValidator,
  http: HttpClient[IO]
) extends StaticFlowStart[IO]
  with CallbackValidator[IO, Email]:
  override val conf: StaticConf = staticConf(authConf)

  override def validate(
    code: Code,
    redirectUrl: FullUrl,
    requestNonce: Option[String]
  ): IO[Either[AuthError, Email]] =
    refreshToken(code, Map(RedirectUri -> redirectUrl.url)).flatMap { tokens =>
      validator.extractEmail(tokens.idToken, Instant.now())
    }

  def refreshToken(code: Code, extraParams: Map[String, String]): IO[RefreshTokenResponse] =
    log.info(s"Exchanging authorization code for tokens...")
    val params = codeParameters(code) ++ extraParams
    http.postFormAs[RefreshTokenResponse](conf.tokenEndpoint, params).map { res =>
      log.info("Exchanged authorization code for refresh token.")
      res
    }

  def verifyRefreshToken(token: RefreshToken): IO[TokenResponse] =
    val params = commonParameters(RefreshTokenValue) ++ Map(
      GrantType -> RefreshTokenValue,
      RefreshTokenValue -> token.value
    )
    http.postFormAs[TokenResponse](conf.tokenEndpoint, params)

  // https://developer.apple.com/documentation/sign_in_with_apple/revoke_tokens
  def revoke(token: RefreshToken): IO[RevokeResult] =
    val params = credentialParameters ++ Map(
      "token" -> token.token,
      "token_type_hint" -> "refresh_token"
    )
    http.postForm(revokeUrl, params).map { res =>
      val shortToken = token.token.take(6)
      log.info(
        s"Revocation of token '$shortToken...' using client id ${authConf.clientId} returned ${res.code}."
      )
      RevokeResult(res.code == 200, res.code, token, authConf.clientId)
    }

  override def extraRedirParams(redirectUrl: FullUrl): Map[String, String] =
    Map(ResponseType -> CodeKey, "response_mode" -> "form_post")

  private def codeParameters(code: Code) =
    commonParameters(AuthorizationCode) ++ Map(CodeKey -> code.code)

  private def commonParameters(grantType: String) = credentialParameters ++ Map(
    GrantType -> grantType
  )

  private def credentialParameters = Map(
    ClientIdKey -> authConf.clientId.value,
    ClientSecretKey -> authConf.clientSecret.value
  )
