package com.malliina.boat.http4s

import com.malliina.boat.auth.AuthProvider.SelectAccount
import com.malliina.boat.auth.{AuthProvider, CookieConf, JWT, UserPayload}
import com.malliina.boat.db.{IdentityError, JWTError, MissingCredentials}
import com.malliina.values.{IdToken, Username}
import org.http4s.Credentials.Token
import org.http4s.headers.{Authorization, Cookie}
import org.http4s.{Headers, HttpDate, Response, ResponseCookie}
import io.circe.*
import io.circe.generic.semiauto.*

import scala.concurrent.duration.DurationInt

class Http4sAuth[F[_]](
  val jwt: JWT,
  val cookieNames: CookieConf = CookieConf.prefixed("boat")
):
  private val cookiePath = Option("/")

  def authenticate(headers: Headers): Either[IdentityError, Username] =
    readUser(cookieNames.user, headers)

  def authState[T: Decoder](from: Headers): Either[IdentityError, T] =
    read[T](cookieNames.authState, from)

  def token(headers: Headers) = headers
    .get[Authorization]
    .toRight(MissingCredentials("Missing Authorization header", headers))
    .flatMap(_.credentials match
      case Token(_, token) => Right(IdToken(token))
      case _               => Left(MissingCredentials("Missing token.", headers))
    )

  def withSession[T: Encoder](t: T, isSecure: Boolean, res: Response[F]): res.Self =
    withJwt(cookieNames.authState, t, isSecure, res)

  def clearSession(res: Response[F]): res.Self =
    res
      .removeCookie(cookieNames.provider)
      .removeCookie(cookieNames.lastId)
      .removeCookie(cookieNames.longTerm)
      .removeCookie(ResponseCookie(cookieNames.authState, "", path = cookiePath))
      .removeCookie(ResponseCookie(cookieNames.user, "", path = cookiePath))
      .addCookie(responseCookie(cookieNames.prompt, SelectAccount))

  def withAppUser(
    user: UserPayload,
    isSecure: Boolean,
    provider: AuthProvider,
    res: Response[F]
  ) = withUser(user, isSecure, res)
    .removeCookie(cookieNames.returnUri)
    .removeCookie(cookieNames.prompt)
    .addCookie(responseCookie(cookieNames.lastId, user.username.name))
    .addCookie(responseCookie(cookieNames.provider, provider.name))

  private def withUser[T: Encoder](t: T, isSecure: Boolean, res: Response[F]): res.Self =
    withJwt(cookieNames.user, t, isSecure, res)

  def withJwt[T: Encoder](
    cookieName: String,
    t: T,
    isSecure: Boolean,
    res: Response[F]
  ): res.Self =
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

  private def responseCookie(name: String, value: String) = ResponseCookie(
    name,
    value,
    Option(HttpDate.MaxValue),
    path = cookiePath,
    secure = true,
    httpOnly = true
  )

  def parseLongTermCookie(headers: Headers) = readToken(cookieNames.longTerm, headers)

  def longTermCookie(token: IdToken) = responseCookie(cookieNames.longTerm, token.value)

  private def readUser(cookieName: String, headers: Headers): Either[IdentityError, Username] =
    read[UserPayload](cookieName, headers).map(_.username)

  def read[T: Decoder](cookieName: String, headers: Headers): Either[IdentityError, T] =
    for
      idToken <- readToken(cookieName, headers)
      t <- jwt
        .verify[T](idToken)
        .left
        .map: err =>
          JWTError(err, headers)
    yield t

  private def readToken(cookieName: String, headers: Headers): Either[MissingCredentials, IdToken] =
    for
      header <- headers.get[Cookie].toRight(MissingCredentials("Cookie parsing error.", headers))
      idToken <-
        header.values
          .find(_.name == cookieName)
          .map(c => IdToken(c.content))
          .toRight(MissingCredentials(s"Cookie not found: '$cookieName'.", headers))
    yield idToken
