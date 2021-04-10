package tests

import cats.effect.{ContextShift, IO}
import com.malliina.boat.BoatConf
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{IdentityException, PushDevice}
import com.malliina.boat.http4s.{AppComps, AppCompsBuilder, Auth}
import com.malliina.boat.push.{BoatNotification, PushEndpoint, PushSummary}
import com.malliina.http.HttpClient
import com.malliina.values.Email
import org.http4s.Headers

object NoopPushEndpoint extends PushEndpoint {
  override def push(notification: BoatNotification, to: PushDevice): IO[PushSummary] =
    IO.pure(PushSummary.empty)
}

object TestEmailAuth extends EmailAuth {
  val testToken = "header.payload.signature"
  val testEmail = Email("test@example.com")

  override def authEmail(headers: Headers): IO[Email] =
    Auth
      .token(headers)
      .fold(
        mc => IO.raiseError(IdentityException(mc)),
        ok => IO.pure(testEmail)
      )
}

class TestComps extends AppComps {
  override val pushService: PushEndpoint = NoopPushEndpoint
  override val emailAuth: EmailAuth = TestEmailAuth
}

object TestComps {
  val builder = new AppCompsBuilder {
    override def apply(conf: BoatConf, http: HttpClient[IO], cs: ContextShift[IO]): AppComps =
      new TestComps
  }
}
