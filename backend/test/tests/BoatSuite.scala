package tests

import akka.actor.ActorSystem
import com.dimafeng.testcontainers.{ForAllTestContainer, MySQLContainer}
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.push._
import com.malliina.boat.{AccessToken, AppBuilder, AppComponents, AppConf}
import com.malliina.play.auth.Auth
import com.malliina.values.Email
import org.scalatest.{FunSuite, Suite}
import play.api.ApplicationLoader.Context
import play.api.mvc.RequestHeader

import scala.concurrent.Future

object TestConf {
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName
  )
}

trait DockerDatabase extends ForAllTestContainer { self: Suite =>
  override val container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")

  def testDatabase(as: ActorSystem, conf: Conf) = BoatDatabase.withMigrations(as, conf)
}

abstract class TestAppSuite
  extends FunSuite
  with OneAppPerSuite2[AppComponents]
  with DockerDatabase {
  override def createComponents(context: Context): AppComponents = {
    container.start()
    TestComponents(context, TestConf(container))
  }
}

object TestComponents {
  def apply(ctx: Context, conf: Conf): AppComponents =
    new AppComponents((_, _, _) => new TestAppBuilder(conf), ctx)
}

class TestAppBuilder(conf: Conf) extends AppBuilder {
  override val appConf = AppConf("", "", "", AccessToken(""))
  override val pushService: PushEndpoint = NoopPushSystem
  override val emailAuth = TestEmailAuth
  override val databaseConf: Conf = conf
  override val isMariaDb: Boolean = true
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
