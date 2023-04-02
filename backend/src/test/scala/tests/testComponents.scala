package tests

import cats.effect.{IO, Sync}
import com.malliina.boat.BoatConf
import com.malliina.boat.auth.{EmailAuth, JWT, SecretKey}
import com.malliina.boat.db.{CustomJwt, IdentityException, JWTError, PushDevice}
import com.malliina.boat.http4s.{AppComps, AppCompsBuilder, Auth}
import com.malliina.boat.push.{BoatNotification, PushEndpoint, PushSummary}
import com.malliina.http.HttpClient
import com.malliina.values.{Email, IdToken, TokenValue}
import com.malliina.web
import com.malliina.web.{AuthError, InvalidSignature, JWTError, KeyConf, ParsedJWT, TokenVerifier, Verified}
import org.http4s.Headers
import tests.TestEmailAuth.testToken

import java.time.Instant

class NoopPushEndpoint[F[_]: Sync] extends PushEndpoint[F]:
  override def push(notification: BoatNotification, to: PushDevice): F[PushSummary] =
    Sync[F].pure(PushSummary.empty)

object TestEmailAuth:
  val testToken = IdToken("header.payload.signature")

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
          else F.raiseError(IdentityException(JWTError(InvalidSignature(ok), headers)))
      )

class TestComps[F[_]: Sync](conf: BoatConf) extends AppComps[F]:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint[F] = NoopPushEndpoint[F]
  override val emailAuth: EmailAuth[F] = TestEmailAuth[F]

object TestComps:
  val builder: AppCompsBuilder = new AppCompsBuilder:
    def build[F[+_]: Sync](conf: BoatConf, http: HttpClient[F]): AppComps[F] =
      TestComps(conf)
