package com.malliina.boat.it

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, SourceQueue}
import akka.{Done, NotUsed}
import com.malliina.boat._
import com.malliina.boat.client.{HttpUtil, WebSocketClient}
import com.malliina.http.FullUrl
import com.malliina.play.models.Password
import controllers.BoatController
import org.scalatest.FunSuite
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Call
import tests.OneServerPerSuite2

import scala.concurrent.{Await, Future}

abstract class TestAppSuite extends ServerSuite(new AppComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends BaseSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

abstract class BaseSuite extends FunSuite {
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T]): T = Await.result(f, 3.seconds)
}

abstract class BoatTests extends TestAppSuite with BoatSockets {
  def openTestBoat(boat: BoatName, creds: Option[Creds] = None) = {
    openBoat(urlFor(reverse.boats()), boat, creds)
  }

  def openViewerSocket(in: Sink[JsValue, Future[Done]], creds: Option[Creds] = None) = {
    val out = Source.maybe[JsValue].mapMaterializedValue(_ => NotUsed)
    val client = openWebSocket(reverse.updates(), BoatNames.random(), creds, in, out)
    client
  }

  def openWebSocket[T](path: Call, boat: BoatName, creds: Option[Creds], in: Sink[JsValue, Future[Done]], out: Source[JsValue, NotUsed]) = {
    openSocket(urlFor(path), boat, in, out, creds)
  }

  def urlFor(call: Call) = FullUrl("ws", s"localhost:$port", call.toString)
}

trait BoatSockets {
  this: BaseSuite =>

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  def openBoat(url: FullUrl, boat: BoatName, creds: Option[Creds] = None) = {
    val (queue, src) = Streaming.sourceQueue[JsValue](mat)
    val socket = openSocket(url, boat, Sink.ignore, src, creds)
    new TestBoat(queue, socket)
  }

  def openSocket[T](url: FullUrl, boat: BoatName, in: Sink[JsValue, Future[Done]], out: Source[JsValue, NotUsed], creds: Option[Creds] = None): WebSocketClient = {
    val authHeaders = creds.map { c =>
      newHeader(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
    }.toList
    val boatHeader = newHeader(BoatController.BoatNameHeader, boat.name)
    val client = WebSocketClient(url, authHeaders :+ boatHeader, as, mat)
    client.connectJson(in, out)
    await(client.initialConnection)
    client
  }

  class TestBoat(val queue: SourceQueue[Option[JsValue]], val socket: WebSocketClient) {
    def send[T: Writes](t: T) = await(queue.offer(Option(Json.toJson(t))))

    def close(): Unit = queue.offer(None)
  }

  private def newHeader(key: String, value: String): HttpHeader = {
    HttpHeader.parse(key, value).asInstanceOf[Ok].header
  }
}

case class Creds(user: User, pass: Password)
