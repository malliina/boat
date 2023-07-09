package tests

import cats.effect.*
import cats.effect.kernel.Resource
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.db.{Conf, DoobieDatabase, DoobieSQL}
import com.malliina.boat.http4s.{JsonInstances, Server, ServerComponents, Service}
import com.malliina.boat.{AisAppConf, BoatConf, Errors, LocalConf}
import com.malliina.http.FullUrl
import com.malliina.http.io.HttpClientF2
import com.malliina.logback.LogbackUtils
import com.malliina.util.AppLogger
import com.typesafe.config.Config
import org.http4s.EntityDecoder
import org.testcontainers.utility.DockerImageName

import java.nio.file.{Path, Paths}
import scala.util.Try

case class TestBoatConf(testdb: Conf)
case class WrappedTestConf(boat: TestBoatConf)

object TestHttp:
  lazy val client = HttpClientF2[IO]()

object WrappedTestConf:
  def parse(c: Config = LocalConf.localConf.resolve()) = Try(
    WrappedTestConf(
      TestBoatConf(BoatConf.parseDatabase(c.getConfig("boat").getConfig("dbtest")))
    )
  )

trait MUnitSuite extends munit.CatsEffectSuite:
  val userHome: Path = Paths.get(sys.props("user.home"))
  def databaseFixture(conf: => Conf) = resource(DoobieDatabase.resource[IO](conf))
  def resource[T](res: Resource[IO, T]) = ResourceFixture(res)
  LogbackUtils.init(rootLevel = Level.WARN)

object TestConf:
  def apply(container: MySQLContainer): Conf = Conf(
    container.jdbcUrl,
    container.username,
    container.password,
    container.driverClassName,
    maxPoolSize = 5,
    autoMigrate = true
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

  val dbFixture = ResourceFixture(
    Resource.eval(IO(confFixture())).flatMap(c => DoobieDatabase.withMigrations(c))
  )

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture)

case class AppComponents(service: Service[IO]) //, routes: HttpApp[IO])

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite:
  self: MUnitSuite =>

  val appResource: Resource[IO, AppComponents] =
    for
      conf <- Resource.eval(IO(confFixture()))
      service <- Server.appService[IO](
        BoatConf.parse().copy(db = conf, ais = AisAppConf(false)),
        TestComps.builder
      )
    yield AppComponents(service)

  val app = ResourceSuiteLocalFixture("munit-boat-app", appResource)

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture, app)

case class ServerTools(server: ServerComponents[IO]):
  def port = server.server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

trait ServerSuite extends MUnitDatabaseSuite with JsonInstances:
  self: MUnitSuite =>
  implicit val tsBody: EntityDecoder[IO, Errors] = jsonBody[IO, Errors]

  def testServerResource: Resource[IO, ServerTools] =
    for
      conf <- Resource.eval(IO(confFixture()))
      service <- Server.server[IO](
        BoatConf.parse().copy(db = conf, ais = AisAppConf(false)),
        TestComps.builder,
        port = port"0"
      )
    yield ServerTools(service)
  val server: Fixture[ServerTools] =
    ResourceSuiteLocalFixture("munit-server", testServerResource)

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture, server)
