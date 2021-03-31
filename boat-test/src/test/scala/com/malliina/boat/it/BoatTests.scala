package com.malliina.boat.it

import akka.stream.scaladsl.{Sink, Source, SourceQueue}
import akka.{Done, NotUsed}
import cats.effect.{ContextShift, IO, Timer}
import com.malliina.boat._
import com.malliina.boat.client.{HttpUtil, KeyValue, WebSocketClient}
import com.malliina.boat.http4s.Service
import com.malliina.http.FullUrl
import com.malliina.values.{Password, Username}
import munit.FunSuite
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Call

import scala.concurrent.Promise
//import play.api.test.{DefaultTestServerFactory, RunningServer}
//import tests.{AkkaStreamsSuite, DockerDatabase, TestAppLoader, TestComponents}

//trait Http4sSuite extends MUnitDatabaseSuite { self: FunSuite =>
//  implicit def munitContextShift: ContextShift[IO] =
//    IO.contextShift(munitExecutionContext)
//
//  implicit def munitTimer: Timer[IO] =
//    IO.timer(munitExecutionContext)
//
//  val app: Fixture[Service] = new Fixture[Service]("pics-app2") {
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

import scala.concurrent.Future

//case class TestServer(server: RunningServer, components: AppComponents)
//
//abstract class ServerSuite extends AkkaStreamsSuite with DockerDatabase {
//  val server: Fixture[TestServer] = new Fixture[TestServer]("boat-server") {
//    private var testServer: TestServer = null
//    def apply(): TestServer = testServer
//    override def beforeAll(): Unit = {
//      val comps = TestComponents(TestAppLoader.createTestAppContext, db())
//      val runningServer = DefaultTestServerFactory.start(comps.application)
//      testServer = TestServer(runningServer, comps)
//    }
//    override def afterAll(): Unit = {
//      testServer.server.stopServer.close()
//    }
//  }
//  def testServer = server().server
//  def components = server().components
//  def port = testServer.endpoints.httpEndpoint.map(_.port).get
//
//  override def munitFixtures: Seq[Fixture[_]] = Seq(db, server)
//}
//
//abstract class BoatTests extends ServerSuite with BoatSockets {
//  def openTestBoat[T](boat: BoatName)(code: TestBoat => T): T = {
//    openBoat(urlFor(reverse.boatSocket()), Left(boat))(code)
//  }
//
//  def openViewerSocket[T](in: Sink[JsValue, Future[Done]], creds: Option[Creds] = None)(
//    code: WebSocketClient => T
//  ): T = {
//    val out = Source.maybe[JsValue].mapMaterializedValue(_ => NotUsed)
//    val headers = creds.map { c =>
//      KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
//    }.toList
//    openWebSocket(reverse.clientSocket(), in, out, headers)(code)
//  }
//
//  def openWebSocket[T](
//    path: Call,
//    in: Sink[JsValue, Future[Done]],
//    out: Source[JsValue, NotUsed],
//    headers: List[KeyValue]
//  )(code: WebSocketClient => T): T = {
//    openSocket(urlFor(path), in, out, headers)(code)
//  }
//
//  def urlFor(call: Call) = FullUrl("ws", s"localhost:$port", call.toString)
//}
//
//trait BoatSockets {
//  this: AkkaStreamsSuite =>
//
//  def openRandomBoat[T](url: FullUrl)(code: TestBoat => T): T =
//    openBoat(url, Left(BoatNames.random()))(code)
//
//  def openBoat[T](url: FullUrl, boat: Either[BoatName, BoatToken])(code: TestBoat => T): T = {
//    val headers = boat.fold(
//      name => KeyValue(Constants.BoatNameHeader, name.name),
//      t => KeyValue(Constants.BoatTokenHeader, t.token)
//    )
//    val (queue, src) = Streaming.sourceQueue[JsValue](mat)
//    openSocket(url, Sink.ignore, src, List(headers)) { client =>
//      code(new TestBoat(queue, client))
//    }
//  }
//
//  def openSocket[T](
//    url: FullUrl,
//    in: Sink[JsValue, Future[Done]],
//    out: Source[JsValue, NotUsed],
//    headers: List[KeyValue]
//  )(code: WebSocketClient => T): T = {
//    val client = WebSocketClient(url, headers, as, mat)
//    try {
//      client.connectJson(in, out)
//      await(client.initialConnection)
//      code(client)
//    } finally {
//      client.close()
//    }
//  }
//
//  class TestBoat(val queue: SourceQueue[Option[JsValue]], val socket: WebSocketClient) {
//    def send[T: Writes](t: T) = await(queue.offer(Option(Json.toJson(t))), 30.seconds)
//
//    def close(): Unit = queue.offer(None)
//  }
//
//}

case class Creds(user: Username, pass: Password)
