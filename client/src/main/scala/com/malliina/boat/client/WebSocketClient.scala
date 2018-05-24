package com.malliina.boat.client

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.malliina.http.FullUrl
import play.api.libs.json.{Json, Writes}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object WebSocketClient {
  def apply(as: ActorSystem, mat: Materializer): WebSocketClient =
    new WebSocketClient(FullUrl.https("boat.malliina.com", "/ws/boats"))(as, mat)
}

class WebSocketClient(url: FullUrl)(implicit as: ActorSystem, mat: Materializer) {
  val scheduler = as.scheduler
  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)

  def connect[T: Writes](out: Source[T, NotUsed]): Future[Done] = {
    val messageSource = out.map(t => TextMessage(Json.stringify(Json.toJson(t))))
    val flow = Flow.fromSinkAndSourceMat(Sink.ignore, messageSource)(Keep.left)
    val (upgrade, closed) = Http().singleWebSocketRequest(WebSocketRequest(url.url), flow)
    upgrade.map { up =>
      if (up.response.status == StatusCodes.SwitchingProtocols) ()
      else ()
    }
    closed.flatMap { done =>
      if (enabled.get()) after(1.second, scheduler)(connect(out))
      else Future.successful(done)
    }
  }

  def close(): Unit = enabled.set(false)
}
