package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Constants.{BoatNameHeader, BoatTokenHeader}
import com.malliina.boat.auth.SettingsPayload
import com.malliina.boat.db.{MissingCredentials, MissingCredentialsException, UserManager}
import com.malliina.boat.http.UserRequest
import com.malliina.boat.http4s.AuthService.GoogleCookie
import com.malliina.boat.{BoatName, BoatNames, BoatToken, DeviceMeta, JoinedBoat, MinimalUserInfo, SimpleBoatMeta, UserBoats, UserInfo, Usernames}
import com.malliina.values.Email
import org.http4s.headers.Cookie
import org.http4s.{Headers, Request, Response}
import org.typelevel.ci.{CIString, CIStringSyntax}

object AuthService {
  val GoogleCookie = ci"google"
  val ProviderCookieName = ci"boatProvider"
}

class AuthService(val users: UserManager, comps: AuthComps) {
  val emailAuth = comps.google
  val web = comps.web
  val googleFlow = comps.googleFlow
  val microsoftFlow = comps.microsoftFlow

  def profile(req: Request[IO]): IO[UserInfo] = profile(req.headers)

  def profile(headers: Headers): IO[UserInfo] = emailOnly(headers).flatMap { email =>
    users.userInfo(email)
  }

  def optionalWebAuth(
    req: Request[IO]
  ): IO[Either[MissingCredentials, UserRequest[Option[UserBoats]]]] = optionalUserInfo(req).map {
    e =>
      e.map { opt => UserRequest(opt, req) }
  }

  def authOrAnon(headers: Headers): IO[MinimalUserInfo] =
    minimal(headers, _ => IO.pure(MinimalUserInfo.anon))

  def typical(headers: Headers) = minimal(headers, mce => IO.raiseError(mce))

  def minimal(headers: Headers, onFail: MissingCredentialsException => IO[MinimalUserInfo]) =
    profile(headers).handleErrorWith {
      case mce: MissingCredentialsException => settings(headers).map(IO.pure).getOrElse(onFail(mce))
      case other                            => IO.raiseError(other)
    }

  def saveSettings(settings: SettingsPayload, res: Response[IO], isSecure: Boolean) = web.withJwt(
    SettingsPayload.cookieName,
    settings,
    isSecure,
    res
  )

  def settings(headers: Headers) =
    web
      .read[SettingsPayload](SettingsPayload.cookieName, headers)
      .toOption
      .filter(_.username != Usernames.anon)

  def authBoat(headers: Headers): IO[DeviceMeta] = authDevice(headers)

  def authDevice(headers: Headers): IO[DeviceMeta] =
    boatToken(headers).getOrElse {
      val boatName = headers
        .get(CIString(BoatNameHeader))
        .map(h => BoatName(h.head.value))
        .getOrElse(BoatNames.random())
      IO.pure(SimpleBoatMeta(Usernames.anon, boatName))
    }

  def boatToken(headers: Headers): Option[IO[JoinedBoat]] =
    headers.get(CIString(BoatTokenHeader)).map { h =>
      users.authBoat(BoatToken(h.head.value))
    }

  private def emailOnly(headers: Headers): IO[Email] =
    emailAuth.authEmail(headers).handleErrorWith {
      case mce: MissingCredentialsException =>
        authSession(headers).map(email => IO.pure(email)).getOrElse(IO.raiseError(mce))
      case t =>
        IO.raiseError(t)
    }

  private def optionalUserInfo(
    req: Request[IO]
  ): IO[Either[MissingCredentials, Option[UserBoats]]] = {
    val headers = req.headers
    authSession(headers).map { email =>
      users.boats(email).map { boats => Right(Option(boats)) }
    }.getOrElse {
      val providerCookieName = comps.web.cookieNames.provider
      val hasGoogleCookie = headers.get[Cookie].exists { header =>
        header.values.exists(r =>
          r.name == providerCookieName && r.content == GoogleCookie.toString
        )
      }
      if (hasGoogleCookie) IO.pure(Left(MissingCredentials(headers)))
      else IO.pure(Right(None))
    }
  }

  private def authSession(headers: Headers) =
    web
      .authenticate(headers)
      .map(user => Email(user.name))
}
