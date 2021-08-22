package com.malliina.boat.ais

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes, Uri}
import akka.pattern.after
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import akka.{Done, NotUsed}
import com.malliina.boat.ais.WebSocketClient.log
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import io.circe.{Encoder, Json}
import io.circe.syntax.EncoderOps
import io.circe.parser.{decode, parse}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

// https://www.digitraffic.fi/meriliikenne/#websocket-rajapinnat
object WebSocketClient {
  private val log = AppLogger(getClass)

  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(
    url: FullUrl,
    headers: List[KeyValue],
    as: ActorSystem,
    mat: Materializer
  ): WebSocketClient =
    new WebSocketClient(url, headers)(as, mat)

  def apply(headers: List[KeyValue], as: ActorSystem, mat: Materializer): WebSocketClient =
    apply(ProdUrl, headers, as, mat)
}

class WebSocketClient(url: FullUrl, headers: List[KeyValue])(implicit
  as: ActorSystem,
  mat: Materializer
) {
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

  def connect[T: Encoder](out: Source[T, NotUsed]): Future[Done] =
    connectInOut(Sink.ignore, out)

  def connectJson[T: Encoder](in: Sink[Json, Future[Done]], out: Source[T, _]): Future[Done] = {
    val incomingSink = in.contramap[Message] {
      case BinaryMessage.Strict(data) =>
        throw new Exception(s"Unsupported binary message: '$data'.")
      case TextMessage.Strict(text) =>
        parse(text).fold(err => throw new Exception(s"Not JSON: '$err'."), identity)
      case other =>
        throw new Exception(s"Unsupported message: '$other'.")
    }
    connectInOut(incomingSink, out)
  }

  def connectInOut[T: Encoder](in: Sink[Message, Future[Done]], out: Source[T, _]): Future[Done] = {
    log.info(s"Connecting to '$url'...")
    val messageSource = out.map(t => TextMessage(t.asJson.noSpaces))
    val flow = Flow.fromSinkAndSourceMat(in, messageSource)(Keep.left).via(switch.flow)
    val (upgrade, closed) =
      Http().singleWebSocketRequest(WebSocketRequest(Uri(url.url), validHeaders), flow)
    upgrade.map { up =>
      initialConnectionPromise.trySuccess(up)
      val code = up.response.status
      if (code == StatusCodes.SwitchingProtocols) {
        log.info(s"WebSocket connected to '$url'.")
      } else {
        log.error(s"WebSocket connection attempt failed with code ${code.intValue()}.")
      }
    }
    closed.recover {
      case t =>
        log.warn(s"WebSocket disconnected.", t)
        Done
    }.flatMap { done =>
      if (enabled.get()) {
        log.warn(s"WebSocket disconnected. Reconnecting after $reconnectInterval...")
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
