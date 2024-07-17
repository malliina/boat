package tests

import cats.effect.IO
import cats.effect.kernel.Resource
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.db.DoobieSQL
import com.malliina.boat.http4s.{JsonInstances, Server, ServerComponents, Service}
import com.malliina.boat.{AisAppConf, BoatConf, LocalConf}
import com.malliina.config.ConfigNode
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.{Errors, FullUrl}
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.util.AppLogger
import munit.AnyFixture
import okhttp3.{OkHttpClient, Protocol}
import org.http4s.EntityDecoder
import org.testcontainers.utility.DockerImageName

import java.nio.file.{Path, Paths}
import java.util
import java.util.concurrent.TimeUnit
import scala.util.Try

case class TestBoatConf(testdb: Conf)
case class WrappedTestConf(boat: TestBoatConf)

object TestHttp:
  val okClient = OkHttpClient
    .Builder()
    .protocols(util.Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
    .connectTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .build()
  lazy val client = HttpClientF2[IO](okClient)

object WrappedTestConf:
  def parse(c: ConfigNode = LocalConf.localConf): Try[WrappedTestConf] =
    c.parse[String]("boat.dbtest.pass")
      .map(dbPass => WrappedTestConf(TestBoatConf(testDbConf(dbPass))))
      .toTry

  private def testDbConf(dbPass: String) = Conf(
    "jdbc:mysql://localhost:3306/boattest",
    "boattest",
    dbPass,
    Conf.MySQLDriver,
    2,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )

trait MUnitSuite extends munit.CatsEffectSuite:
  val userHome: Path = Paths.get(sys.props("user.home"))
  def databaseFixture(conf: => Conf) = resource(DoobieDatabase.default[IO](conf))
  def resource[T](res: Resource[IO, T]) = ResourceFunFixture(res)
  LogbackUtils.init(rootLevel = Level.WARN)

object TestConf:
  def apply(container: MySQLContainer): Conf = Conf(
    container.jdbcUrl,
    container.username,
    container.password,
    container.driverClassName,
    maxPoolSize = 2,
    autoMigrate = true,
    schemaTable = "flyway_schema_history2"
  )

object MUnitDatabaseSuite:
  private val log = AppLogger(getClass)

trait MUnitDatabaseSuite extends DoobieSQL:
  self: MUnitSuite =>
  import MUnitDatabaseSuite.log

  val confFixture: Fixture[Conf] = new Fixture[Conf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit =
      val isCi = sys.env.get("CI").contains("true")
      val testDb =
        if isCi then dockerConf()
        else
          readTestConf().fold(
            e =>
              log.warn(s"Failed to read test conf. Falling back to Docker...", e)
              dockerConf()
            ,
            ok => ok
          )
      conf = Option(testDb)

    override def afterAll(): Unit =
      container.foreach(_.stop())

    def readTestConf(): Try[Conf] = WrappedTestConf.parse().map(_.boat.testdb)

    def dockerConf() =
      val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:8.0.33"))
      c.start()
      container = Option(c)
      TestConf(c)

  val dbFixture = ResourceFunFixture(
    Resource.eval(IO(confFixture())).flatMap(c => DoobieDatabase.init(c))
  )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture)

case class AppComponents(service: Service[IO])

trait BoatDatabaseSuite extends MUnitDatabaseSuite:
  self: MUnitSuite =>
  def boatIO = for
    conf <- IO(confFixture())
    boatConf <- BoatConf.parseF[IO].map(_.copy(isTest = true, db = conf, ais = AisAppConf(false)))
  yield boatConf

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends BoatDatabaseSuite:
  self: MUnitSuite =>

  val appResource: Resource[IO, AppComponents] =
    for
      boatConf <- Resource.eval(boatIO)
      service <- Server.appService[IO](boatConf, TestComps.builder)
    yield AppComponents(service)

  val app = ResourceSuiteLocalFixture("munit-boat-app", appResource)

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture, app)

case class ServerTools(server: ServerComponents[IO]):
  def port = server.server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends BoatDatabaseSuite with JsonInstances:
  self: MUnitSuite =>
  given EntityDecoder[IO, Errors] = jsonBody[IO, Errors]

  def testServerResource: Resource[IO, ServerTools] =
    for
      boatConf <- Resource.eval(boatIO)
      service <- Server.server[IO](boatConf, TestComps.builder, port = port"0")
    yield ServerTools(service)
  val server =
    ResourceSuiteLocalFixture("munit-server", testServerResource)

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture, server)
