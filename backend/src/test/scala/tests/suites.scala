package tests

import cats.effect.unsafe.IORuntime
import cats.effect.kernel.Resource
import cats.effect.*
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.db.{Conf, DoobieDatabase, DoobieSQL}
import com.malliina.boat.http4s.{JsonInstances, Server, ServerComponents, Service}
import com.malliina.boat.{BoatConf, Errors, LocalConf}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import com.typesafe.config.Config
import munit.FunSuite
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.{EntityDecoder, EntityEncoder, HttpApp, Uri}
import org.testcontainers.utility.DockerImageName

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.util.Try

case class TestBoatConf(testdb: Conf)
case class WrappedTestConf(boat: TestBoatConf)

object WrappedTestConf:
  def parse(c: Config = LocalConf.localConf.resolve()) = Try(
    WrappedTestConf(
      TestBoatConf(BoatConf.parseDatabase(c.getConfig("boat").getConfig("testdb")))
    )
  )

case class TestResource[T](resource: T, close: IO[Unit])

trait MUnitSuite extends FunSuite:
  val userHome = Paths.get(sys.props("user.home"))
  implicit val rt: IORuntime = cats.effect.unsafe.implicits.global
  implicit val ec: ExecutionContext = munitExecutionContext

  def databaseFixture(conf: => Conf): FunFixture[DoobieDatabase] = resource {
    DoobieDatabase(conf)
  }

  def resource[T](res: Resource[IO, T]): FunFixture[T] =
    var finalizer: Option[IO[Unit]] = None
    FunFixture(
      setup = opts =>
        val (t, f) = res.allocated.unsafeRunSync()
        finalizer = Option(f)
        t,
      teardown = t => finalizer.foreach(_.unsafeRunSync())
    )

object TestConf:
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName,
    maxPoolSize = 5
  )

object MUnitDatabaseSuite:
  private val log = AppLogger(getClass)

trait MUnitDatabaseSuite extends DoobieSQL:
  self: MUnitSuite =>
  import MUnitDatabaseSuite.log
  val dbFixture = resource(DoobieDatabase.withMigrations(confFixture()))

  val confFixture: Fixture[Conf] = new Fixture[Conf]("database"):
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit =
      val testDb = readTestConf().fold(
        e =>
          log.warn(s"Failed to read test conf. Falling back to Docker...", e)
          val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:5.7.29"))
          c.start()
          container = Option(c)
          TestConf(c),
        ok => ok
      )
      conf = Option(testDb)
    override def afterAll(): Unit =
      container.foreach(_.stop())

    def readTestConf(): Try[Conf] = WrappedTestConf.parse().map(_.boat.testdb)

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture)

case class AppComponents(service: Service, routes: HttpApp[IO])

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite:
  self: MUnitSuite =>
  val app: Fixture[AppComponents] = new Fixture[AppComponents]("boat-app"):
    private var service: Option[AppComponents] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))

    override def apply(): AppComponents = service.get

    override def beforeAll(): Unit =
      val resource = Server.appService(BoatConf.parse().copy(db = confFixture()), TestComps.builder)
      val (t, release) = resource.allocated.unsafeRunSync()
      finalizer.set(release)
      service = Option(AppComponents(t, Server.makeHandler(t)))

    override def afterAll(): Unit =
      finalizer.get().unsafeRunSync()

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture, app)

case class ServerTools(server: ServerComponents, client: Client[IO]):
  def port = server.server.address.getPort
  def baseHttpUri = Uri.unsafeFromString(s"http://localhost:$port")
  def baseWsUri = Uri.unsafeFromString(s"ws://localhost:$port")
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")

object ServerSuite:
  private val log = AppLogger(getClass)

trait ServerSuite extends MUnitDatabaseSuite with JsonInstances:
  self: MUnitSuite =>
  import ServerSuite.log
  implicit val tsBody: EntityDecoder[IO, Errors] = jsonBody[IO, Errors]

  val server: Fixture[ServerTools] = new Fixture[ServerTools]("server"):
    private var tools: Option[ServerTools] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))

    override def apply(): ServerTools = tools.get

    override def beforeAll(): Unit =
      val testServer =
        Server.server(BoatConf.parse().copy(db = confFixture()), TestComps.builder, port = 12345)
      val testClient = BlazeClientBuilder[IO](munitExecutionContext).resource
      val (instance, closable) = testServer.flatMap { s =>
        testClient.map { c => ServerTools(s, c) }
      }.allocated.unsafeRunSync()
      tools = Option(instance)
      finalizer.set(closable)

    override def afterAll(): Unit =
      finalizer.get().unsafeRunSync()

  override def munitFixtures: Seq[Fixture[?]] = Seq(confFixture, server)
