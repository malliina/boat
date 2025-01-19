package com.malliina.boat

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.kernel.Resource
import ch.qos.logback.classic.Level
import com.comcast.ip4s.port
import com.malliina.boat.db.DoobieSQL
import com.malliina.boat.http4s.{JsonInstances, Server, ServerComponents, ServerResources, Service}
import com.malliina.config.{ConfigError, ConfigNode, MissingValue}
import com.malliina.database.{Conf, DoobieDatabase}
import com.malliina.http.UrlSyntax.url
import com.malliina.http.io.HttpClientF2
import com.malliina.http.{CSRFConf, Errors, FullUrl}
import com.malliina.http4s.CSRFUtils
import com.malliina.logback.LogbackUtils
import com.malliina.values.Password
import munit.AnyFixture
import okhttp3.{OkHttpClient, Protocol}
import org.http4s.EntityDecoder
import org.http4s.server.middleware.CSRF

import java.nio.file.{Path, Paths}
import java.util
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class TestBoatConf(testdb: Conf)

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

trait MUnitSuite extends munit.CatsEffectSuite:
  val userHome: Path = Paths.get(sys.props("user.home"))
  def databaseFixture(conf: => Conf) = resource(DoobieDatabase.default[IO](conf))
  def resource[T](res: Resource[IO, T]) = ResourceFunFixture(res)
  LogbackUtils.init(rootLevel = Level.OFF)

trait MUnitDatabaseSuite extends DoobieSQL:
  self: MUnitSuite =>
  val testConf: ConfigNode = LocalConf.local("test-boat.conf")

  val confFixture: Fixture[Conf] = new Fixture[Conf]("database"):
    var conf: Either[ConfigError, Conf] = Left(MissingValue(NonEmptyList.of("password")))

    def apply(): Conf = conf.fold(err => throw err, ok => ok)

    override def beforeAll(): Unit =
      conf = testConf
        .parse[Password]("boat.db.pass")
        .map: pass =>
          testDatabaseConf(pass)

    override def afterAll(): Unit = ()

    private def testDatabaseConf(password: Password) = Conf(
      url"jdbc:mysql://127.0.0.1:3306/testboat",
      "testboat",
      password,
      Conf.MySQLDriver,
      maxPoolSize = 2,
      autoMigrate = true,
      schemaTable = "flyway_schema_history2"
    )

  val dbFixture = ResourceFunFixture(
    Resource.eval(IO(confFixture())).flatMap(c => DoobieDatabase.init(c))
  )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture)

case class AppComponents(service: Service[IO])

trait BoatDatabaseSuite extends MUnitDatabaseSuite:
  self: MUnitSuite =>
  def boatIO = for
    conf <- IO(confFixture())
    node <- IO.fromEither(testConf.parse[ConfigNode]("boat"))
    boatConf <- IO.fromEither(
      BoatConf.parse(node, pass => conf, ais = AisAppConf(false), isTest = true)
    )
  yield boatConf

// https://github.com/typelevel/munit-cats-effect
trait Http4sSuite extends BoatDatabaseSuite:
  self: MUnitSuite =>
  val appResource: Resource[IO, AppComponents] =
    val csrfConf = CSRFConf.default
    val csrfUtils = CSRFUtils(csrfConf)
    for
      csrf <- Resource.eval[IO, CSRF[IO, IO]](
        csrfUtils.default[IO](onFailure = CSRFUtils.defaultFailure[IO])
      )
      boatConf <- Resource.eval(boatIO)
      service <- Server.appService[IO](boatConf, TestComps.builder, csrf, csrfConf)
    yield AppComponents(service)

  val app = ResourceSuiteLocalFixture("munit-boat-app", appResource)

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture, app)

case class ServerTools(server: ServerComponents[IO]):
  def port = server.server.address.getPort
  def baseHttpUrl = FullUrl("http", s"localhost:$port", "")
  def baseWsUrl = FullUrl("ws", s"localhost:$port", "")
  def csrf = server.app.csrfConf

trait ServerSuite extends BoatDatabaseSuite with JsonInstances:
  self: MUnitSuite =>
  given EntityDecoder[IO, Errors] = jsonBody[IO, Errors]

  object TestServer extends ServerResources:
    LogbackUtils.init(rootLevel = Level.OFF)

  def testServerResource: Resource[IO, ServerTools] =
    for
      boatConf <- Resource.eval(boatIO)
      service <- TestServer.server[IO](boatConf, TestComps.builder, port = port"0")
    yield ServerTools(service)

  def serverWithReleaseTimeout = releaseTimeout(testServerResource, 10.seconds)

  val server =
    ResourceSuiteLocalFixture("munit-server", serverWithReleaseTimeout)

  private def releaseTimeout[R](r: Resource[IO, R], duration: FiniteDuration): Resource[IO, R] =
    Resource
      .eval(r.allocated)
      .flatMap: (r1, fin) =>
        Resource.make(IO.pure(r1))(rel =>
          fin.timeoutTo(duration, IO(println(s"Resource release timed out after $duration.")))
        )

  override def munitFixtures: Seq[AnyFixture[?]] = Seq(confFixture, server)
