package com.malliina.boat.client

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes, Uri}
import akka.pattern.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import akka.{Done, NotUsed}
import com.malliina.boat.client.WebSocketClient.log
import com.malliina.http.FullUrl
import play.api.libs.json.{JsValue, Json, Writes}

import scala.concurrent.{Future, Promise}

object WebSocketClient {
  private val log = Logging(getClass)

  val ProdUrl = FullUrl.wss("www.boat-tracker.com", "/ws/boats")

  def apply(url: FullUrl, headers: List[KeyValue], as: ActorSystem, mat: Materializer): WebSocketClient =
    new WebSocketClient(url, headers)(as, mat)

  def apply(headers: List[KeyValue], as: ActorSystem, mat: Materializer): WebSocketClient =
    apply(ProdUrl, headers, as, mat)
}

class WebSocketClient(url: FullUrl, headers: List[KeyValue])(implicit as: ActorSystem, mat: Materializer) {
  val validHeaders = headers.map(kv => HttpHeader.parse(kv.key, kv.value)).collect {
    case Ok(header, _) => header
  }
  val scheduler = as.scheduler
  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)
  val reconnectInterval = 2.seconds
  private val initialConnectionPromise = Promise[WebSocketUpgradeResponse]()
  val initialConnection = initialConnectionPromise.future
  val switch = KillSwitches.shared(s"WebSocket-$url")

  def connect[T: Writes](out: Source[T, NotUsed]): Future[Done] =
    connectInOut(Sink.ignore, out)

  /** Connects to WebSocket server.
    *
    * @param in sink for messages received from the server
    * @param out source of messages sent to the server
    * @tparam T type of message (JsValue works)
    * @return a Future that completes when the connection is terminated
    */
  def connectJson[T: Writes](in: Sink[JsValue, Future[Done]], out: Source[T, NotUsed]): Future[Done] = {
    val incomingSink = in.contramap[Message] {
      case BinaryMessage.Strict(data) => Json.parse(data.iterator.asInputStream)
      case TextMessage.Strict(text) => Json.parse(text)
      case other => throw new Exception(s"Unsupported message: '$other'.")
    }
    connectInOut(incomingSink, out)
  }

  def connectInOut[T: Writes](in: Sink[Message, Future[Done]], out: Source[T, NotUsed]): Future[Done] = {
    log.info(s"Connecting to '$url'...")
    val messageSource = out.map { t =>
      TextMessage(Json.stringify(Json.toJson(t)))
    }
    val flow = Flow.fromSinkAndSourceMat(in, messageSource)(Keep.left).via(switch.flow)
    val (upgrade, closed) = Http().singleWebSocketRequest(WebSocketRequest(Uri(url.url), validHeaders), flow)
    upgrade.map { up =>
      initialConnectionPromise.trySuccess(up)
      val code = up.response.status
      if (code == StatusCodes.SwitchingProtocols) {
        log.info(s"WebSocket connected to '$url'.")
      } else {
        log.error(s"WebSocket connection attempt failed with code ${code.intValue()}.")
      }
    }
    closed.recover { case t =>
      log.warn(s"WebSocket disconnected.", t)
      Done
    }.flatMap { done =>
      if (enabled.get()) {
        log.warn(s"WebSocket disconnected. Reconnecting to '$url' after $reconnectInterval...")
        after(reconnectInterval, scheduler)(connectInOut(in, out))
      } else {
        log.info(s"WebSocket disconnected. No more reconnects.")
        Future.successful(done)
      }
    }
  }

  def close(): Unit = {
    enabled.set(false)
    switch.shutdown()
  }
}
