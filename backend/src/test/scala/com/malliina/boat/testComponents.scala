package com.malliina.boat

import cats.effect.{IO, Resource, Sync}
import com.malliina.boat.TestEmailAuth.{expiredToken, testToken}
import com.malliina.boat.auth.{EmailAuth, JWT}
import com.malliina.boat.db.{CustomJwt, IdentityException, JWTError, PushDevice}
import com.malliina.boat.http4s.{AppComps, AppCompsBuilder, Auth}
import com.malliina.boat.push.{PushEndpoint, PushGeo, PushSummary, SourceNotification}
import com.malliina.http.OkHttpHttpClient
import com.malliina.http.io.HttpClientF2
import com.malliina.values.{Email, IdToken}
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
  val testToken = IdToken("header.payload.signature")
  val expiredToken = IdToken("header.payload.expired")

class TestEmailAuth[F[_]: Sync] extends EmailAuth[F]:
  val F = Sync[F]
  val testEmail = Email("test@example.com")

  override def authEmail(headers: Headers, now: Instant): F[Email] =
    Auth
      .token(headers)
      .fold(
        mc => F.raiseError(IdentityException(mc)),
        ok =>
          if ok == testToken then F.pure(testEmail)
          else if ok == expiredToken then
            F.raiseError(
              WebAuthException(Expired(ok, now.minus(10, ChronoUnit.MINUTES), now), headers)
            )
          else F.raiseError(IdentityException(JWTError(InvalidSignature(ok), headers)))
      )

class TestComps(conf: BoatConf) extends AppComps[IO]:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint[IO] = NoopPushEndpoint[IO]
  override val emailAuth: EmailAuth[IO] = TestEmailAuth[IO]

object TestComps:
  val builder: AppCompsBuilder[IO] = new AppCompsBuilder[IO]:
    override def http: Resource[IO, HttpClientF2[IO]] =
      Resource.pure(TestHttp.client)
    override def build(conf: BoatConf, http: OkHttpHttpClient[IO]): AppComps[IO] =
      TestComps(conf)
