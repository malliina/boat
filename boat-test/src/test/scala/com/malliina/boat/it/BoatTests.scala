package com.malliina.boat.it

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, SourceQueue}
import akka.{Done, NotUsed}
import com.malliina.boat._
import com.malliina.boat.client.{HttpUtil, KeyValue, WebSocketClient}
import com.malliina.http.FullUrl
import com.malliina.play.models.Password
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponents
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Call
import tests.{BaseSuite, OneServerPerSuite2}

import scala.concurrent.Future

abstract class TestAppSuite extends ServerSuite(new AppComponents(_))

abstract class ServerSuite[T <: BuiltInComponents](build: Context => T)
  extends BaseSuite
    with OneServerPerSuite2[T] {
  override def createComponents(context: Context) = build(context)
}

abstract class BoatTests extends TestAppSuite with BoatSockets {
  def openTestBoat[T](boat: BoatName, creds: Option[Creds] = None)(code: TestBoat => T): T = {
    openBoat(urlFor(reverse.boats()), boat, creds)(code)
  }

  def openViewerSocket[T](in: Sink[JsValue, Future[Done]], creds: Option[Creds] = None)(code: WebSocketClient => T): T = {
    val out = Source.maybe[JsValue].mapMaterializedValue(_ => NotUsed)
    openWebSocket(reverse.updates(), BoatNames.random(), creds, in, out)(code)
  }

  def openWebSocket[T](path: Call, boat: BoatName, creds: Option[Creds], in: Sink[JsValue, Future[Done]], out: Source[JsValue, NotUsed])(code: WebSocketClient => T): T = {
    openSocket(urlFor(path), boat, in, out, creds)(code)
  }

  def urlFor(call: Call) = FullUrl("ws", s"localhost:$port", call.toString)
}

trait BoatSockets {
  this: BaseSuite =>

  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  def openBoat[T](url: FullUrl, boat: BoatName, creds: Option[Creds] = None)(code: TestBoat => T): T = {
    val (queue, src) = Streaming.sourceQueue[JsValue](mat)
    openSocket(url, boat, Sink.ignore, src, creds) { client =>
      code(new TestBoat(queue, client))
    }
  }

  def openSocket[T](url: FullUrl, boat: BoatName, in: Sink[JsValue, Future[Done]], out: Source[JsValue, NotUsed], creds: Option[Creds] = None)(code: WebSocketClient => T): T = {
    val authHeaders = creds.map { c =>
      KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
    }.toList
    val boatHeader = KeyValue(Constants.BoatNameHeader, boat.name)
    val client = WebSocketClient(url, authHeaders :+ boatHeader, as, mat)
    try {
      client.connectJson(in, out)
      await(client.initialConnection)
      code(client)
    } finally {
      client.close()
    }
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
