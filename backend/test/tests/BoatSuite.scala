package tests

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.push._
import com.malliina.boat.{AccessToken, AppBuilder, AppComponents, AppConf, LocalConf}
import com.malliina.play.auth.Auth
import com.malliina.values.Email
import play.api.ApplicationLoader.Context
import play.api.{Configuration, Play}
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

trait MUnitAppSuite extends DockerDatabase { self: munit.Suite =>
  val app: Fixture[AppComponents] = new Fixture[AppComponents]("boat-app") {
    private var comps: AppComponents = null
    def apply() = comps
    override def beforeAll(): Unit = {
      comps = TestComponents(
        TestAppLoader.createTestAppContext,
        db()
      )
      Play.start(comps.application)
    }
    override def afterAll(): Unit = {
      Play.stop(comps.application)
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
}

trait DockerDatabase { self: munit.Suite =>
  val db: Fixture[Conf] = new Fixture[Conf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
      val dbc =
        Conf.fromDatabaseConf(LocalConf.localConf.get[Configuration]("boat.testdb")).getOrElse {
          val c = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
          c.start()
          container = Option(c)
          TestConf(c)
        }
      conf = Option(dbc)
    }
    override def afterAll(): Unit = {
      container.foreach(_.stop())
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

abstract class TestAppSuite extends munit.FunSuite with MUnitAppSuite

object TestComponents {
  def apply(ctx: Context, conf: Conf): AppComponents =
    new AppComponents((_, _, _) => new TestAppBuilder(conf), ctx)
}

class TestAppBuilder(conf: Conf) extends AppBuilder {
  override val appConf = AppConf("", "", "", AccessToken(""))
  override val pushService: PushEndpoint = NoopPushSystem
  override val emailAuth = TestEmailAuth
  override val databaseConf: Conf = conf
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
