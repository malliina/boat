package com.malliina.boat.http4s

import cats.effect.Sync
import cats.syntax.all.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import com.malliina.boat.Constants.{BoatNameHeader, BoatTokenHeader}
import com.malliina.boat.auth.{BoatJwt, SettingsPayload}
import com.malliina.boat.db.*
import com.malliina.boat.http.UserRequest
import com.malliina.boat.http4s.AuthService.GoogleCookie
import com.malliina.boat.{BoatName, BoatNames, BoatToken, DeviceMeta, JoinedSource, MinimalUserInfo, SimpleSourceMeta, SourceType, UserBoats, UserInfo, Usernames}
import com.malliina.values.Email
import com.malliina.web.{Code, RevokeResult}
import org.http4s.headers.Cookie
import org.http4s.{Headers, Request, Response}
import org.typelevel.ci.{CIString, CIStringSyntax}
import cats.syntax.all.toTraverseOps
import java.time.Instant

object AuthService:
  val GoogleCookie = ci"google"

class AuthService[F[_]: Sync](val users: IdentityManager[F], comps: AuthComps[F]):
  val F = Sync[F]
  val emailAuth = comps.google
  val web = comps.web
  val googleFlow = comps.googleFlow
  val microsoftBoatFlow = comps.microsoftBoatFlow
  val microsoftCarFlow = comps.microsoftCarFlow
  val appleWebFlow = comps.appleWebFlow
  private val appSiwa: SIWADatabase[F] = SIWADatabase(comps.appleAppFlow, users, comps.customJwt)
  val webSiwa: SIWADatabase[F] = SIWADatabase(comps.appleWebFlow, users, comps.customJwt)

  def delete(headers: Headers, now: Instant): F[List[RevokeResult]] =
    for
      user <- profile(headers, now)
      tokens <- users.refreshTokens(user.id)
      revocations <- tokens.traverse: token =>
        for
          app <- comps.appleAppFlow.revoke(token)
          web <- comps.appleWebFlow.revoke(token)
        yield List(app, web)
      _ <- users.deleteUser(user.username)
    yield revocations.flatten

  def register(code: Code, now: Instant): F[BoatJwt] = appSiwa.registerApp(code, now)

  def profile(req: Request[F]): F[UserInfo] = profile(req.headers)

  private def profileMini(headers: Headers): F[MinimalUserInfo] =
    profile(headers).map(ui => ui: MinimalUserInfo)

  def profile(headers: Headers, now: Instant = Instant.now()): F[UserInfo] =
    emailOnly(headers, now).flatMap(email => users.userInfo(email))

  def recreate(headers: Headers, now: Instant = Instant.now()): F[BoatJwt] =
    Auth
      .token(headers)
      .map(token => appSiwa.recreate(token, now))
      .fold(mce => F.raiseError(MissingCredentialsException(mce)), identity)

  def optionalWebAuth(
    req: Request[F]
  ): F[Either[MissingCredentials, UserRequest[F, Option[UserBoats]]]] =
    optionalUserInfo(req).map(e => e.map(opt => UserRequest(opt, req)))

  def authOrAnon(headers: Headers): F[MinimalUserInfo] =
    minimal(headers, _ => F.pure(MinimalUserInfo.anon))

  def typical(headers: Headers) = minimal(headers, mce => F.raiseError(mce))

  private def minimal(headers: Headers, onFail: MissingCredentialsException => F[MinimalUserInfo]) =
    profileMini(headers).handleErrorWith:
      case mce: MissingCredentialsException => settings(headers).map(F.pure).getOrElse(onFail(mce))
      case other                            => F.raiseError(other)

  def saveSettings(settings: SettingsPayload, res: Response[F], isSecure: Boolean) = web.withJwt(
    SettingsPayload.cookieName,
    settings,
    isSecure,
    res
  )

  def settings(headers: Headers): Option[SettingsPayload] =
    web
      .read[SettingsPayload](SettingsPayload.cookieName, headers)
      .toOption
      .filter(_.username != Usernames.anon)

  def authBoat(headers: Headers): F[DeviceMeta] =
    boatToken(headers)
      .map(e => e.map(jb => jb: DeviceMeta))
      .getOrElse:
        val boatName = headers
          .get(CIString(BoatNameHeader))
          .map(h => BoatName(h.head.value))
          .getOrElse(BoatNames.random())
        F.pure(SimpleSourceMeta(Usernames.anon, boatName, SourceType.Boat): DeviceMeta)

  private def boatToken(headers: Headers): Option[F[JoinedSource]] =
    headers.get(CIString(BoatTokenHeader)).map(h => users.authBoat(BoatToken(h.head.value)))

  private def emailOnly(headers: Headers, now: Instant): F[Email] =
    emailAuth
      .authEmail(headers, now)
      .handleErrorWith:
        case mce: MissingCredentialsException =>
          authSession(headers).fold(
            err => F.raiseError(mce),
            email => F.pure(email)
          )
        case t =>
          F.raiseError(t)

  private def optionalUserInfo(
    req: Request[F]
  ): F[Either[MissingCredentials, Option[UserBoats]]] =
    val headers = req.headers
    authSession(headers)
      .map: email =>
        users.boats(email).map(boats => Right(Option(boats)))
      .getOrElse:
        val providerCookieName = comps.web.cookieNames.provider
        val hasGoogleCookie = headers.get[Cookie].exists { header =>
          header.values
            .exists(r => r.name == providerCookieName && r.content == GoogleCookie.toString)
        }
        if hasGoogleCookie then F.pure(Left(MissingCredentials(headers)))
        else F.pure(Right(None))

  private def authSession(headers: Headers) =
    web
      .authenticate(headers)
      .map(user => Email(user.name))
