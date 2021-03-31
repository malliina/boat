package tests

import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.flatMap._
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import munit.FunSuite
import org.testcontainers.utility.DockerImageName
import pureconfig.{ConfigObjectSource, ConfigSource}

import scala.concurrent.Promise
import scala.util.Try

case class TestPicsConf(testdb: String)
case class WrappedTestConf(pics: TestPicsConf)

object Suites extends FunSuite {
  val httpClient = FunFixture[HttpClient[IO]](
    setup = opts => HttpClientIO(),
    teardown = http => http.close()
  )
}

//trait MUnitDatabaseSuite { self: munit.Suite =>
//  val db: Fixture[DatabaseConf] = new Fixture[DatabaseConf]("database") {
//    var container: Option[MySQLContainer] = None
//    var conf: Option[DatabaseConf] = None
//    def apply() = conf.get
//    override def beforeAll(): Unit = {
//      val testDb = readTestConf.getOrElse {
//        val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:5.7.29"))
//        c.start()
//        container = Option(c)
//        TestConf(c)
//      }
//      conf = Option(testDb)
//    }
//    override def afterAll(): Unit = {
//      container.foreach(_.stop())
//    }
//
//    def readTestConf = {
//      import pureconfig.generic.auto.exportReader
//
//      Try(
//        ConfigObjectSource(Right(LocalConf.localConfig))
//          .withFallback(ConfigSource.default)
//          .loadOrThrow[WrappedTestConf]
//          .pics
//          .testdb
//      )
//    }
//  }
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
//}

// https://github.com/typelevel/munit-cats-effect
//trait Http4sSuite extends MUnitDatabaseSuite { self: FunSuite =>
//  implicit def munitContextShift: ContextShift[IO] =
//    IO.contextShift(munitExecutionContext)
//
//  implicit def munitTimer: Timer[IO] =
//    IO.timer(munitExecutionContext)
//
//  val app: Fixture[AppService] = new Fixture[AppService]("pics-app2") {
//    private var service: Option[AppService] = None
//    val promise = Promise[IO[Unit]]()
//
//    override def apply(): AppService = service.get
//
//    override def beforeAll(): Unit = {
//      val resource =
//        PicsServer.appResource(PicsConf.load.copy(db = db()), MultiSizeHandlerIO.empty())
//      val resourceEffect = resource.allocated[IO, AppService]
//      val setupEffect =
//        resourceEffect.map {
//          case (t, release) =>
//            promise.success(release)
//            t
//        }
//          .flatTap(t => IO.pure(()))
//
//      service = Option(await(setupEffect.unsafeToFuture()))
//    }
//
//    override def afterAll(): Unit = {
//      val f = IO
//        .pure(())
//        .flatMap(_ => IO.fromFuture(IO(promise.future))(munitContextShift).flatten)
//        .unsafeToFuture()
//      await(f)
//    }
//  }
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
//}
