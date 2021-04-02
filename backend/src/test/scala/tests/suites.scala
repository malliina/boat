package tests

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import com.dimafeng.testcontainers.MySQLContainer
import com.malliina.boat.db.{Conf, DoobieDatabase}
import com.malliina.boat.http4s.{Server, Service}
import com.malliina.boat.{BoatConf, LocalConf}
import com.malliina.http.HttpClient
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import munit.FunSuite
import org.testcontainers.utility.DockerImageName
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigObjectSource, ConfigSource}

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

case class TestBoatConf(testdb: Conf)
case class WrappedTestConf(boat: TestBoatConf)
case class TestResource[T](resource: T, close: IO[Unit])

trait MUnitSuite extends FunSuite {
  val userHome = Paths.get(sys.props("user.home"))
  val blocker = Blocker[IO]

  implicit def munitContextShift: ContextShift[IO] =
    IO.contextShift(munitExecutionContext)

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

object Suites extends FunSuite {
  val httpClient = FunFixture[HttpClient[IO]](
    setup = opts => HttpClientIO(),
    teardown = http => http.close()
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
      import pureconfig.generic.auto.exportReader
      LocalConf().load[WrappedTestConf].fold(f => Left(f), c => Right(c.boat.testdb))
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db)
}

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends MUnitDatabaseSuite { self: MUnitSuite =>
  implicit def munitTimer: Timer[IO] =
    IO.timer(munitExecutionContext)

  val app: Fixture[Service] = new Fixture[Service]("boat-app") {
    private var service: Option[Service] = None
    val finalizer = new AtomicReference[IO[Unit]](IO.pure(()))

    override def apply(): Service = service.get

    override def beforeAll(): Unit = {
      val resource = Server.appService(BoatConf.load.copy(db = db()), TestComps.builder)
      val (t, release) = resource.allocated[IO, Service].unsafeRunSync()
      finalizer.set(release)
      service = Option(t)
    }

    override def afterAll(): Unit = {
      finalizer.get().unsafeRunSync()
    }
  }

  override def munitFixtures: Seq[Fixture[_]] = Seq(db, app)
}
