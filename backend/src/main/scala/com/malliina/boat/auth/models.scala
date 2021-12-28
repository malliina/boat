package com.malliina.boat.auth

import com.malliina.boat.db.RefreshTokenId
import com.malliina.boat.{BoatName, Language, MinimalUserInfo, SingleError}
import com.malliina.values.{Email, Password, Username}
import io.circe.*
import io.circe.generic.semiauto.*

case class SecretKey(value: String) extends AnyVal:
  override def toString = "****"

case class BasicCredentials(username: Username, password: Password)

case class CookieConf(
  user: String,
  authState: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String
)

object CookieConf:
  def prefixed(prefix: String) = CookieConf(
    s"$prefix-user",
    s"$prefix-state",
    s"$prefix-return-uri",
    s"$prefix-last-id",
    s"$prefix-provider",
    s"$prefix-prompt"
  )

case class SettingsPayload(username: Username, language: Language, authorized: Seq[BoatName])
  extends MinimalUserInfo

object SettingsPayload:
  val cookieName = "boat-settings"
  implicit val json: Codec[SettingsPayload] = deriveCodec[SettingsPayload]

case class UserPayload(username: Username)

object UserPayload:
  implicit val json: Codec[UserPayload] = deriveCodec[UserPayload]

  def email(email: Email): UserPayload = apply(Username(email.value))

sealed abstract class AuthProvider(val name: String)

object AuthProvider:
  val PromptKey = "prompt"
  val SelectAccount = "select_account"
  val all = Seq(Google, Microsoft)

  def forString(s: String): Either[SingleError, AuthProvider] =
    all
      .find(_.name == s)
      .toRight(SingleError.input(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

  case object Google extends AuthProvider("google")
  case object Microsoft extends AuthProvider("microsoft")
  case object Apple extends AuthProvider("apple")

case class JWTClaims(email: Email, refresh: RefreshTokenId)

object JWTClaims:
  implicit val json: Codec[JWTClaims] = deriveCodec[JWTClaims]
