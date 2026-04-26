package com.malliina.boat

import cats.effect.{IO, Resource, Sync}
import com.malliina.boat.TestEmailAuth.{expiredToken, testToken, mobileToken}
import com.malliina.boat.auth.{EmailAuth, JWT}
import com.malliina.boat.db.{CustomJwt, IdentityException, JWTError, PushDevice}
import com.malliina.boat.http4s.{AppComps, AppCompsBuilder, Auth}
import com.malliina.boat.push.{PushEndpoint, PushGeo, PushSummary, SourceNotification}
import com.malliina.http.HttpClient
import com.malliina.values.{Email, IdToken}
import com.malliina.values.Literals.{email, jwt}
import com.malliina.web.{Expired, InvalidSignature, WebAuthException}
import org.http4s.Headers

import java.time.Instant
import java.time.temporal.ChronoUnit

class NoopPushEndpoint[F[_]: Sync] extends PushEndpoint[F]:
  override def push(
    notification: SourceNotification,
    geo: PushGeo,
    to: PushDevice,
    now: Instant
  ): F[PushSummary] =
    Sync[F].pure(PushSummary.empty)

object TestEmailAuth:
  val testToken = jwt"header.payload.signature".id
  val mobileToken = jwt"header.mobile-payload.signature".id
  val expiredToken = jwt"header.payload.expired".id

class TestEmailAuth[F[_]: Sync] extends EmailAuth[F]:
  val F = Sync[F]
  val testEmail = email"test@example.com"
  val testMobileEmail = email"mobile@example.com"

  override def authEmail(headers: Headers, now: Instant): F[Email] =
    Auth
      .token(headers)
      .fold(
        mc => F.raiseError(IdentityException(mc)),
        ok =>
          ok match
            case `testToken`    => F.pure(testEmail)
            case `mobileToken`  => F.pure(testMobileEmail)
            case `expiredToken` =>
              F.raiseError(
                WebAuthException(Expired(ok, now.minus(10, ChronoUnit.MINUTES), now), headers)
              )
            case _ =>
              F.raiseError(IdentityException(JWTError(InvalidSignature(ok), headers)))
      )

class TestComps(conf: BoatConf) extends AppComps[IO]:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint[IO] = NoopPushEndpoint[IO]
  override val emailAuth: EmailAuth[IO] = TestEmailAuth[IO]

object TestComps:
  val builder: AppCompsBuilder[IO] = new AppCompsBuilder[IO]:
    override def http: Resource[IO, HttpClient[IO]] =
      Resource.pure(TestHttp.client)
    override def build(conf: BoatConf, http: HttpClient[IO]): AppComps[IO] =
      TestComps(conf)
