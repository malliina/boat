package tests

import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db._
import com.malliina.boat.push._
import com.malliina.boat.{AccessToken, AppBuilder, AppComponents, AppConf}
import com.malliina.play.auth.Auth
import com.malliina.values.Email
import play.api.ApplicationLoader.Context
import play.api.Play
import play.api.mvc.RequestHeader

import scala.concurrent.{ExecutionContext, Future}

object TestConf {
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName
  )
}

trait MUnitAppSuite { self: munit.Suite =>
  val app: Fixture[AppComponents] = new Fixture[AppComponents]("boat-app") {
    private var container: Option[MySQLContainer] = None
    private var comps: AppComponents = null
    def apply() = comps
    override def beforeAll(): Unit = {
      val db = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
      db.start()
      container = Option(db)
      comps = TestComponents(
        TestAppLoader.createTestAppContext,
        TestConf(db)
      )
      Play.start(comps.application)
    }
    override def afterAll(): Unit = {
      Play.stop(comps.application)
      container.foreach(_.stop())
    }
  }

  override def munitFixtures = Seq(app)
}

trait DockerDatabase { self: munit.Suite =>
  val db: Fixture[MySQLContainer] = new Fixture[MySQLContainer]("database") {
    var container: MySQLContainer = null
    def apply() = container
    override def beforeAll(): Unit = {
      container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
      container.start()
    }
    override def afterAll(): Unit = {
      container.stop()
    }
  }

  override def munitFixtures = Seq(db)

  def testDatabase(ec: ExecutionContext, conf: Conf) = BoatDatabase.withMigrations(ec, conf)
}

//trait DockerDatabase extends ForAllTestContainer { self: Suite =>
//  override val container = MySQLContainer(mysqlImageVersion = "mysql:5.7.29")
//
//  def testDatabase(as: ActorSystem, conf: Conf) = BoatDatabase.withMigrations(as, conf)
//}

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
