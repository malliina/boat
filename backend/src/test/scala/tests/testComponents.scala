package tests

import cats.effect.IO
import com.malliina.boat.BoatConf
import com.malliina.boat.auth.{EmailAuth, JWT, SecretKey}
import com.malliina.boat.db.{CustomJwt, IdentityException, PushDevice}
import com.malliina.boat.http4s.{AppComps, AppCompsBuilder, Auth}
import com.malliina.boat.push.{BoatNotification, PushEndpoint, PushSummary}
import com.malliina.http.HttpClient
import com.malliina.values.{Email, TokenValue}
import com.malliina.web
import com.malliina.web.{AuthError, InvalidSignature, JWTError, KeyConf, ParsedJWT, TokenVerifier, Verified}
import org.http4s.Headers

import java.time.Instant

object NoopPushEndpoint extends PushEndpoint:
  override def push(notification: BoatNotification, to: PushDevice): IO[PushSummary] =
    IO.pure(PushSummary.empty)

object TestEmailAuth extends EmailAuth:
  val testToken = "header.payload.signature"
  val testEmail = Email("test@example.com")

  override def authEmail(headers: Headers, now: Instant): IO[Email] =
    Auth
      .token(headers)
      .fold(
        mc => IO.raiseError(IdentityException(mc)),
        ok => IO.pure(testEmail)
      )

class TestComps(conf: BoatConf) extends AppComps:
  override val customJwt: CustomJwt = CustomJwt(JWT(conf.secret))
  override val pushService: PushEndpoint = NoopPushEndpoint
  override val emailAuth: EmailAuth = TestEmailAuth

object TestComps:
  val builder: AppCompsBuilder = (conf: BoatConf, http: HttpClient[IO]) => TestComps(conf)
