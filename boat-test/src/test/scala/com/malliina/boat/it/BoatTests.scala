package com.malliina.boat.it

import akka.stream.scaladsl.{Sink, Source, SourceQueue}
import akka.{Done, NotUsed}
import com.malliina.boat._
import com.malliina.boat.client.{HttpUtil, KeyValue, WebSocketClient}
import com.malliina.http.FullUrl
import com.malliina.values.{Password, Username}
import org.http4s.Uri
import play.api.libs.json.{JsValue, Json, Writes}
import tests.{AkkaStreamsSuite, ServerSuite}

import scala.concurrent.Future

abstract class BoatTests extends AkkaStreamsSuite with ServerSuite with BoatSockets {
  def openTestBoat[T](boat: BoatName)(code: TestBoat => T): T =
    openBoat(urlFor(reverse.ws.boats), Left(boat))(code)

  def openViewerSocket[T](in: Sink[JsValue, Future[Done]], creds: Option[Creds] = None)(
    code: WebSocketClient => T
  ): T = {
    val out = Source.maybe[JsValue].mapMaterializedValue(_ => NotUsed)
    val headers = creds.map { c =>
      KeyValue(HttpUtil.Authorization, HttpUtil.authorizationValue(c.user, c.pass.pass))
    }.toList
    openWebSocket(reverse.ws.updates, in, out, headers)(code)
  }

  def openWebSocket[T](
    path: Uri,
    in: Sink[JsValue, Future[Done]],
    out: Source[JsValue, NotUsed],
    headers: List[KeyValue]
  )(code: WebSocketClient => T): T = {
    openSocket(urlFor(path), in, out, headers)(code)
  }

  def urlFor(call: Uri): FullUrl = server().baseWsUrl.append(call.renderString)
}

trait BoatSockets { self: AkkaStreamsSuite =>
  def openRandomBoat[T](url: FullUrl)(code: TestBoat => T): T =
    openBoat(url, Left(BoatNames.random()))(code)

  def openBoat[T](url: FullUrl, boat: Either[BoatName, BoatToken])(code: TestBoat => T): T = {
    val headers = boat.fold(
      name => KeyValue(Constants.BoatNameHeader, name.name),
      t => KeyValue(Constants.BoatTokenHeader, t.token)
    )
    val (queue, src) = Streaming.sourceQueue[JsValue](mat)
    openSocket(url, Sink.ignore, src, List(headers)) { client =>
      code(new TestBoat(queue, client))
    }
  }

  def openSocket[T](
    url: FullUrl,
    in: Sink[JsValue, Future[Done]],
    out: Source[JsValue, NotUsed],
    headers: List[KeyValue]
  )(code: WebSocketClient => T): T = {
    val client = WebSocketClient(url, headers, as, mat)
    try {
      client.connectJson(in, out)
      await(client.initialConnection)
      code(client)
    } finally {
      client.close()
    }
  }

  class TestBoat(val queue: SourceQueue[Option[JsValue]], val socket: WebSocketClient) {
    def send[T: Writes](t: T) = await(queue.offer(Option(Json.toJson(t))), 30.seconds)
    def close(): Unit = queue.offer(None)
  }
}

case class Creds(user: Username, pass: Password)
