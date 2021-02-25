package com.malliina.boat.auth

import com.malliina.boat.{Language, MinimalUserInfo, SingleError}
import com.malliina.values.{Email, Password, Username}
import play.api.libs.json.Json

case class SecretKey(value: String) extends AnyVal {
  override def toString = "****"
}

case class BasicCredentials(username: Username, password: Password)

case class CookieConf(
  user: String,
  session: String,
  returnUri: String,
  lastId: String,
  provider: String,
  prompt: String
)

object CookieConf {
  def prefixed(prefix: String) = CookieConf(
    s"$prefix-user",
    s"$prefix-state",
    s"$prefix-return-uri",
    s"$prefix-last-id",
    s"$prefix-provider",
    s"$prefix-prompt"
  )
}

case class SettingsPayload(username: Username, language: Language) extends MinimalUserInfo

object SettingsPayload {
  val cookieName = "boat-settings"
  implicit val json = Json.format[SettingsPayload]
}

case class UserPayload(username: Username)

object UserPayload {
  implicit val json = Json.format[UserPayload]

  def email(email: Email): UserPayload = apply(Username(email.value))
}

sealed abstract class AuthProvider(val name: String)

object AuthProvider {
  val PromptKey = "prompt"
  val SelectAccount = "select_account"

  def forString(s: String): Either[SingleError, AuthProvider] =
    Seq(Google)
      .find(_.name == s)
      .toRight(SingleError.input(s"Unknown auth provider: '$s'."))

  def unapply(str: String): Option[AuthProvider] =
    forString(str).toOption

  case object Google extends AuthProvider("google")
}
