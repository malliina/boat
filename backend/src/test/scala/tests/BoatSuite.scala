package tests

import cats.effect.IO
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.auth.EmailAuth
import com.malliina.boat.db.{Conf, IdentityException, MissingCredentials, PushDevice}
import com.malliina.boat.http4s.Auth
import com.malliina.boat.push.{BoatNotification, PushEndpoint, PushSummary}
import com.malliina.values.Email
import org.http4s.Headers

//import com.dimafeng.testcontainers.MySQLContainer
//import com.malliina.boat.auth.EmailAuth
//import com.malliina.boat.db._
//import com.malliina.boat.push._
//import com.malliina.boat.{AccessToken, AppComps, AppComponents, AppConf, LocalConf}
//import com.malliina.play.auth.Auth
//import com.malliina.values.Email
//import org.testcontainers.utility.DockerImageName
//import play.api.ApplicationLoader.Context
//import play.api.{Configuration, Play}
//import play.api.mvc.RequestHeader
//
//import scala.concurrent.Future
//import scala.util.Try
//

//
//trait MUnitAppSuite extends DockerDatabase { self: munit.Suite =>
//  val app: Fixture[AppComponents] = new Fixture[AppComponents]("boat-app") {
//    private var comps: AppComponents = null
//    def apply() = comps
//    override def beforeAll(): Unit = {
//      comps = TestComponents(
//        TestAppLoader.createTestAppContext,
//        db()
//      )
//      Play.start(comps.application)
//    }
//    override def afterAll(): Unit = {
//      Play.stop(comps.application)
//    }
//  }
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
//}
//
//trait DockerDatabase { self: munit.Suite =>
//  val db: Fixture[Conf] = new Fixture[Conf]("database") {
//    var container: Option[MySQLContainer] = None
//    var conf: Option[Conf] = None
//    def apply() = conf.get
//    override def beforeAll(): Unit = {
//      val localTestDb =
//        Try(LocalConf.localConf.get[Configuration]("boat.testdb")).toEither.flatMap { c =>
//          Conf.fromDatabaseConf(c)
//        }
//      val testDb = localTestDb.getOrElse {
//        val image = DockerImageName.parse("mysql:5.7.29")
//        val c = MySQLContainer(mysqlImageVersion = image)
//        c.start()
//        container = Option(c)
//        TestConf(c)
//      }
//      conf = Option(testDb)
//    }
//    override def afterAll(): Unit = {
//      container.foreach(_.stop())
//    }
//  }
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
//}
//
//abstract class TestAppSuite extends munit.FunSuite with MUnitAppSuite
//
//object TestComponents {
//  def apply(ctx: Context, conf: Conf): AppComponents =
//    new AppComponents((_, _, _) => new TestAppBuilder(conf), ctx)
//}
//
//class TestAppBuilder(conf: Conf) extends AppComps {
//  override val appConf = AppConf("", "", "", AccessToken(""))
//  override val pushService: PushEndpoint = NoopPushSystem
//  override val emailAuth = TestEmailAuth
//  override val databaseConf: Conf = conf
//}
//
