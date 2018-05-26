package com.malliina.boat.client

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.model.{HttpHeader, StatusCodes, Uri}
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.malliina.boat.client.WebSocketClient.log
import com.malliina.http.FullUrl
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object WebSocketClient {
  private val log = Logging(getClass)

  val ProdUrl = FullUrl.https("boat.malliina.com", "/ws/boats")

  def apply(headers: List[HttpHeader], as: ActorSystem, mat: Materializer): WebSocketClient =
    new WebSocketClient(ProdUrl, headers)(as, mat)
}

class WebSocketClient(url: FullUrl, headers: List[HttpHeader])(implicit as: ActorSystem, mat: Materializer) {
  val scheduler = as.scheduler
  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)
  val reconnectInterval = 2.seconds

  def connect[T: Writes](out: Source[T, NotUsed]): Future[Done] =
    connectInOut(Sink.ignore, out)

  def connectJson[T: Writes](in: Sink[JsValue, Future[Done]], out: Source[T, NotUsed]): Future[Done] = {
    val incomingSink = in.contramap[Message] {
      case BinaryMessage.Strict(data) => Json.parse(data.iterator.asInputStream)
      case TextMessage.Strict(text) => Json.parse(text)
    }
    connectInOut(incomingSink, out)
  }

  def connectInOut[T: Writes](in: Sink[Message, Future[Done]], out: Source[T, NotUsed]): Future[Done] = {
    val messageSource = out.map(t => TextMessage(Json.stringify(Json.toJson(t))))
    val flow = Flow.fromSinkAndSourceMat(in, messageSource)(Keep.left)
    val (upgrade, closed) = Http().singleWebSocketRequest(WebSocketRequest(Uri(url.url), headers), flow)
    upgrade.map { up =>
      val code = up.response.status
      if (code == StatusCodes.SwitchingProtocols) {
        log.info(s"WebSocket connected to '$url'.")
      } else {
        log.error(s"WebSocket connection attempt failed with code ${code.intValue()}.")
      }
    }
    closed.flatMap { done =>
      if (enabled.get()) {
        log.warn(s"WebSocket disconnected. Reconnecting after $reconnectInterval...")
        after(reconnectInterval, scheduler)(connect(out))
      } else {
        Future.successful(done)
      }
    }
  }

  def close(): Unit = enabled.set(false)
}
