package tests

import cats.effect.IO
import com.malliina.boat.BoatConf
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{IdentityException, PushDevice}
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

  override def authEmail(headers: Headers): IO[Email] =
    Auth
      .token(headers)
      .fold(
        mc => IO.raiseError(IdentityException(mc)),
        ok => IO.pure(testEmail)
      )

class TestComps extends AppComps:
  override val pushService: PushEndpoint = NoopPushEndpoint
  override val appleValidator: TokenVerifier = new TokenVerifier(Nil):
    override protected def validateClaims(
      parsed: ParsedJWT,
      now: Instant
    ): Either[JWTError, ParsedJWT] =
      Left(InvalidSignature(parsed.token))
    override def validateToken(token: TokenValue, now: Instant): IO[Either[AuthError, Verified]] =
      IO.raiseError(new Exception("Not supported."))
  override val emailAuth: EmailAuth = TestEmailAuth

object TestComps:
  val builder: AppCompsBuilder = (conf: BoatConf, http: HttpClient[IO]) => new TestComps
