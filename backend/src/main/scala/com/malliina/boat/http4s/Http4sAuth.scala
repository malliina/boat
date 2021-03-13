package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.auth.{AuthProvider, CookieConf, JWT, UserPayload}
import com.malliina.values.{IdToken, Username}
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie}
import org.http4s.{Headers, HttpDate, Response, ResponseCookie}
import play.api.libs.json.{OWrites, Reads, Writes}

import scala.concurrent.duration.DurationInt

object Http4sAuth {
  def apply(jwt: JWT): Http4sAuth = new Http4sAuth(jwt)
}

class Http4sAuth(
  val jwt: JWT,
  val cookieNames: CookieConf = CookieConf.prefixed("boat")
) {
  val cookiePath = Option("/")

  def authenticate(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def authState[T: Reads](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.authState, from)

  def token(headers: Headers) = headers
    .get(Authorization)
    .toRight(MissingCredentials("Missing Authorization header", headers))
    .flatMap(_.credentials match {
      case Token(_, token) => Right(IdToken(token))
      case _               => Left(MissingCredentials("Missing token.", headers))
    })

  def withSession[T: OWrites](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.authState, t, isSecure, res)

  def clearSession(res: Response[IO]): res.Self =
    res
      .removeCookie(cookieNames.provider)
      .removeCookie(cookieNames.lastId)
      .removeCookie(ResponseCookie(cookieNames.authState, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))

  def withAppUser(
    user: UserPayload,
    isSecure: Boolean,
    provider: AuthProvider,
    res: Response[IO]
  ) = withUser(user, isSecure, res)
    .removeCookie(cookieNames.returnUri)
    .addCookie(responseCookie(cookieNames.lastId, user.username.name))
    .addCookie(responseCookie(cookieNames.provider, provider.name))

  def withUser[T: Writes](t: T, isSecure: Boolean, res: Response[IO]): res.Self =
    withJwt(cookieNames.user, t, isSecure, res)

  def withJwt[T: Writes](
    cookieName: String,
    t: T,
    isSecure: Boolean,
    res: Response[IO]
  ): res.Self = {
    val signed = jwt.sign[T](t, 12.hours)
    res.addCookie(
      ResponseCookie(
        cookieName,
        signed.value,
        httpOnly = true,
        secure = isSecure,
        path = cookiePath
      )
    )
  }

  def responseCookie(name: String, value: String) = ResponseCookie(
    name,
    value,
    Option(HttpDate.MaxValue),
    path = cookiePath,
    secure = true,
    httpOnly = true
  )

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  def read[T: Reads](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for {
      header <- Cookie.from(headers).toRight(MissingCredentials("Cookie parsing error.", headers))
      cookie <-
        header.values
          .find(_.name == cookieName)
          .map(c => IdToken(c.content))
          .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
      t <- jwt.verify[T](cookie).left.map { err =>
        JWTError(err, headers)
      }
    } yield t
}
