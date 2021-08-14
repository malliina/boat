package tests

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.db.{Conf, DoobieDatabase}
import com.malliina.boat.http4s.{JsonInstances, Server, ServerComponents, Service}
import com.malliina.boat.{BoatConf, Errors, LocalConf}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import munit.FunSuite
import org.http4s.{HttpApp, Uri}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.testcontainers.utility.DockerImageName
import pureconfig.{CamelCase, ConfigFieldMapping}
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

case class TestBoatConf(testdb: Conf)
case class WrappedTestConf(boat: TestBoatConf)
case class TestResource[T](resource: T, close: IO[Unit])

trait MUnitSuite extends FunSuite {
  val userHome = Paths.get(sys.props("user.home"))
  val blocker = Blocker[IO]

  implicit val ec: ExecutionContext = munitExecutionContext
  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)
  implicit def munitTimer: Timer[IO] =
    IO.timer(munitExecutionContext)
  implicit def conc = IO.ioConcurrentEffect(munitContextShift)
  def databaseFixture(conf: => Conf) = resourceFixture {
    blocker.flatMap { b =>
      DoobieDatabase(conf, b)
    }
  }

  def resourceFixture[T](res: Resource[IO, T]) = FunFixture[TestResource[T]](
    setup = { options =>
      val (t, finalizer) = res.allocated.unsafeRunSync()
      TestResource(t, finalizer)
    },
    teardown = { tr =>
      tr.close.unsafeRunSync()
    }
  )
}

object TestConf {
  def apply(container: MySQLContainer) = Conf(
    s"${container.jdbcUrl}?useSSL=false",
    container.username,
    container.password,
    container.driverClassName,
    maxPoolSize = 5
  )
}

object MUnitDatabaseSuite {
  private val log = AppLogger(getClass)
}

trait MUnitDatabaseSuite { self: MUnitSuite =>
  import MUnitDatabaseSuite.log
  val doobieDb = resourceFixture(blocker.flatMap { b =>
    DoobieDatabase.withMigrations(db(), b)
  })

  val db: Fixture[Conf] = new Fixture[Conf]("database") {
    var container: Option[MySQLContainer] = None
    var conf: Option[Conf] = None
    def apply() = conf.get
    override def beforeAll(): Unit = {
      val testDb = readTestConf().fold(
        err => {
          log.warn(s"Failed to read test conf. ${err.head.description}. Falling back to Docker...")
          val c = MySQLContainer(mysqlImageVersion = DockerImageName.parse("mysql:5.7.29"))
          c.start()
          container = Option(c)
          TestConf(c)
        },
        ok => ok
      )
      conf = Option(testDb)
    }
    override def afterAll(): Unit = {
      container.foreach(_.stop())
    }

    def readTestConf(): Either[ConfigReaderFailures, Conf] = {
      import pureconfig.generic.auto._
      implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

      LocalConf().load[WrappedTestConf].fold(f => Left(f), c => Right(c.boat.testdb))
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

case class AppComponents(service: Service, routes: HttpApp[IO])

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite { self: MUnitSuite =>
  val app: Fixture[AppComponents] = new Fixture[AppComponents]("boat-app") {
    private var service: Option[AppComponents] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))

    override def apply(): AppComponents = service.get

    override def beforeAll(): Unit = {
      val resource = Server.appService(BoatConf.load.copy(db = db()), TestComps.builder)
      val (t, release) = resource.allocated[IO, Service].unsafeRunSync()
      finalizer.set(release)
      service = Option(AppComponents(t, Server.makeHandler(t, t.blocker)))
    }

    override def afterAll(): Unit = {
      finalizer.get().unsafeRunSync()
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
}

case class ServerTools(server: ServerComponents, client: Client[IO]) {
  def port = server.server.address.getPort
  def baseHttpUri = Uri.unsafeFromString(s"http://localhost:$port")
  def baseWsUri = Uri.unsafeFromString(s"ws://localhost:$port")
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")
}

trait ServerSuite extends MUnitDatabaseSuite with JsonInstances { self: MUnitSuite =>
  implicit val tsBody = jsonBody[IO, Errors]

  val server: Fixture[ServerTools] = new Fixture[ServerTools]("server") {
    private var tools: Option[ServerTools] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))

    override def apply(): ServerTools = tools.get

    override def beforeAll(): Unit = {
      val testServer = Server.server(BoatConf.load.copy(db = db()), TestComps.builder, port = 12345)
      val testClient = BlazeClientBuilder[IO](munitExecutionContext, None).resource
      val (instance, closable) = testServer.flatMap { s =>
        testClient.map { c => ServerTools(s, c) }
      }.allocated.unsafeRunSync()
      tools = Option(instance)
      finalizer.set(closable)
    }

    override def afterAll(): Unit = {
      finalizer.get().unsafeRunSync()
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, server)
}
