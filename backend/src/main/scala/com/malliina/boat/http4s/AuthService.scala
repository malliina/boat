package com.malliina.boat.http4s

import cats.effect.IO
import com.malliina.boat.Constants.{BoatNameHeader, BoatTokenHeader}
import com.malliina.boat.auth.{BoatJwt, SettingsPayload}
import com.malliina.boat.db.{IdentityManager, MissingCredentials, MissingCredentialsException, SIWADatabase, UserManager}
import com.malliina.boat.http.UserRequest
import com.malliina.boat.http4s.AuthService.GoogleCookie
import com.malliina.boat.{BoatName, BoatNames, BoatToken, DeviceMeta, JoinedBoat, MinimalUserInfo, SimpleBoatMeta, UserBoats, UserInfo, Usernames}
import com.malliina.values.{Email, IdToken}
import org.http4s.headers.Cookie
import org.http4s.{Headers, Request, Response}
import org.typelevel.ci.{CIString, CIStringSyntax}
import com.malliina.web.{Code, RevokeResult}

import java.time.Instant

object AuthService:
  val GoogleCookie = ci"google"
  val ProviderCookieName = ci"boatProvider"

class AuthService(val users: IdentityManager, comps: AuthComps):
  val emailAuth = comps.google
  val web = comps.web
  val googleFlow = comps.googleFlow
  val microsoftFlow = comps.microsoftFlow
  val appleWebFlow = comps.appleWebFlow
  val appSiwa: SIWADatabase = SIWADatabase(comps.appleAppFlow, users, comps.customJwt)
  val webSiwa: SIWADatabase = SIWADatabase(comps.appleWebFlow, users, comps.customJwt)

  def delete(headers: Headers, now: Instant): IO[List[RevokeResult]] =
    for
      user <- profile(headers, now)
      tokens <- users.refreshTokens(user.id)
      revocations <- IO.parTraverseN(1)(tokens) { token =>
        for
          app <- comps.appleAppFlow.revoke(token)
          web <- comps.appleWebFlow.revoke(token)
        yield List(app, web)
      }
      _ <- users.deleteUser(user.username)
    yield revocations.flatten

  def register(code: Code, now: Instant): IO[BoatJwt] = appSiwa.registerApp(code, now)

  def profile(req: Request[IO]): IO[UserInfo] = profile(req.headers)

  def profile(headers: Headers, now: Instant = Instant.now()): IO[UserInfo] =
    emailOnly(headers, now).flatMap { email =>
      users.userInfo(email)
    }

  def recreate(headers: Headers, now: Instant = Instant.now()): IO[BoatJwt] =
    Auth
      .token(headers)
      .map(token => appSiwa.recreate(token, now))
      .fold(mce => IO.raiseError(MissingCredentialsException(mce)), identity)

  def optionalWebAuth(
    req: Request[IO]
  ): IO[Either[MissingCredentials, UserRequest[Option[UserBoats]]]] =
    optionalUserInfo(req).map { e =>
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

  private def emailOnly(headers: Headers, now: Instant): IO[Email] =
    emailAuth.authEmail(headers, now).handleErrorWith {
      case mce: MissingCredentialsException =>
        authSession(headers).fold(
          err => IO.raiseError(mce),
          email => IO.pure(email)
        )
      case t =>
        IO.raiseError(t)
    }

  private def optionalUserInfo(
    req: Request[IO]
  ): IO[Either[MissingCredentials, Option[UserBoats]]] =
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
      if hasGoogleCookie then IO.pure(Left(MissingCredentials(headers)))
      else IO.pure(Right(None))
    }

  private def authSession(headers: Headers) =
    web
      .authenticate(headers)
      .map(user => Email(user.name))

  private def authOrRenewSession(headers: Headers) = authSession(headers)
