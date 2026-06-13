package com.malliina.boat.auth

import com.malliina.boat.db.RefreshTokenId
import com.malliina.boat.{DeviceName, Language, MinimalUserInfo}
import com.malliina.config.ConfigReadable
import com.malliina.http.SingleError
import com.malliina.values.{Email, IdToken, Password, Readable, Username}
import com.malliina.web.JWTError
import io.circe.Codec

import java.time.Instant

case class SecretKey(value: String) extends AnyVal:
  override def toString = "****"

object SecretKey:
  val dev = SecretKey("app-jwt-signing-secret-goes-here-must-be-sufficiently-long")

  given ConfigReadable[SecretKey] = ConfigReadable.string.map(apply)

case class BasicCredentials(username: Username, password: Password)

case class CookieConf(
  user: String,
  authState: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String,
  longTerm: String
)

object CookieConf:
  def prefixed(prefix: String) = CookieConf(
    s"$prefix-user",
    s"$prefix-state",
    s"$prefix-return-uri",
    s"$prefix-last-id",
    s"$prefix-provider",
    s"$prefix-prompt",
    s"$prefix-auth"
  )

case class SettingsPayload(username: Username, language: Language, authorized: Seq[DeviceName])
  extends MinimalUserInfo derives Codec.AsObject

object SettingsPayload:
  val cookieName = "boat-settings"

case class UserPayload(username: Username) derives Codec.AsObject

object UserPayload:
  def email(email: Email): UserPayload = apply(Username.fromEmail(email))

enum AuthProvider(val name: String):
  case Google extends AuthProvider("google")
  case Microsoft extends AuthProvider("microsoft")
  case Apple extends AuthProvider("apple")

object AuthProvider:
  val PromptKey = "prompt"
  val SelectAccount = "select_account"
  val all: Seq[AuthProvider] = Seq(Google, Microsoft)

  given Readable[AuthProvider] = Readable.string.emap(s => forString(s).left.map(_.message))

  def forString(s: String): Either[SingleError, AuthProvider] =
    all
      .find(_.name == s)
      .toRight(SingleError.input(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

case class BoatJwtClaims(email: Email, refresh: RefreshTokenId, lastValidation: Instant)
  derives Codec.AsObject

case class BoatJwt(email: Email, idToken: IdToken) derives Codec.AsObject

class JWTException(val error: JWTError) extends Exception(error.message.message):
  def message = error.message
