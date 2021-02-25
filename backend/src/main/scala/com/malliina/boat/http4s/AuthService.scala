package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Constants.BoatTokenHeader
import com.malliina.boat.auth.{SettingsPayload, UserPayload}
import com.malliina.boat.db.{IdentityException, UserManager}
import com.malliina.boat.http.UserRequest
import com.malliina.boat.http4s.AuthService.{GoogleCookie, ProviderCookieName}
import com.malliina.boat.{BoatToken, MinimalUserInfo, Usernames}
import com.malliina.values.Email
import org.http4s.headers.Cookie
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Headers, Request, Response}

object AuthService {
  val GoogleCookie = CaseInsensitiveString("google")
  val ProviderCookieName = CaseInsensitiveString("boatProvider")
}

class AuthService(val users: UserManager, comps: AuthComps) {
  val google = comps.google
  val web = comps.web
  val flow = comps.flow

  def profile(headers: Headers) = emailOnly(headers).flatMap { email =>
    users.userInfo(email)
  }

  def optionalWebAuth(req: Request[IO]) = optionalUserInfo(req).map { opt =>
    UserRequest(opt, req)
  }

  def authOrAnon(headers: Headers) = minimal(headers, _ => IO.pure(MinimalUserInfo.anon))

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

  def boatToken(headers: Headers) =
    headers.get(CaseInsensitiveString(BoatTokenHeader)).map { h =>
      users.authBoat(BoatToken(h.value))
    }

  private def emailOnly(headers: Headers) =
    google.authEmail(headers).handleErrorWith {
      case mce: MissingCredentialsException =>
        authSession(headers).map(email => IO.pure(email)).getOrElse(IO.raiseError(mce))
      case t =>
        IO.raiseError(t)
    }

  private def optionalUserInfo(req: Request[IO]) = {
    val headers = req.headers
    authSession(headers).map { email =>
      users.boats(email).map { boats => Option(boats) }
    }.getOrElse {
      val hasGoogleCookie =
        Cookie.from(headers).exists(c => c.name == ProviderCookieName && c.value == GoogleCookie)
      if (hasGoogleCookie) IO.raiseError(IdentityException.missingCredentials(headers))
      else IO.pure(None)
    }
  }

  private def authSession(headers: Headers) =
    web
      .session[UserPayload](headers)
      .map(payload => Email(payload.username.name))
}
