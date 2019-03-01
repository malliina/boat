package tests

import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{IdentityException, MissingCredentials, PushDevice}
import com.malliina.boat.push._
import com.malliina.boat.{AccessToken, AppBuilder, AppComponents, AppConf}
import com.malliina.play.auth.Auth
import com.malliina.values.Email
import play.api.ApplicationLoader.Context
import play.api.mvc.RequestHeader

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class TestAppSuite extends AppSuite(TestComponents.apply) with CoordHelper {
  implicit val ec: ExecutionContext = components.executionContext

  def await[T](f: Future[T], duration: Duration = 10.seconds): T = Await.result(f, duration)
}

object TestComponents {
  def apply(ctx: Context) = new AppComponents((_, _, _) => TestAppBuilder, ctx)
}

object TestAppBuilder extends AppBuilder {
  override val appConf = AppConf("", "", "", AccessToken(""))
  override val pushService: PushEndpoint = NoopPushSystem
  override val emailAuth = TestEmailAuth
}

object NoopPushSystem extends PushEndpoint {
  override def push(notification: BoatNotification, to: PushDevice): Future[PushSummary] =
    Future.successful(PushSummary.empty)
}

object TestEmailAuth extends EmailAuth {
  val testToken = "header.payload.signature"
  val testEmail = Email("test@example.com")

  override def authEmail(rh: RequestHeader): Future[Email] =
    if (Auth.readAuthToken(rh).contains(testToken)) Future.successful(testEmail)
    else Future.failed(IdentityException(MissingCredentials(rh)))
}
