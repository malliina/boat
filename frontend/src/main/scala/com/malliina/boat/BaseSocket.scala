package com.malliina.boat

import cats.effect.std.Dispatcher
import com.malliina.http.FullUrl
import fs2.concurrent.Topic
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, Event, MessageEvent}

enum WebSocketEvent(val event: Event):
  case Open(e: Event) extends WebSocketEvent(e)
  case Message(e: MessageEvent) extends WebSocketEvent(e)
  case Close(e: CloseEvent) extends WebSocketEvent(e)
  case Error(e: Event) extends WebSocketEvent(e)

class BaseSocket[F[_]](
  wsPath: String,
  messages: Topic[F, WebSocketEvent],
  d: Dispatcher[F],
  val log: BaseLogger = BaseLogger.console
):
  val socket: dom.WebSocket = openSocket(wsPath)

  private def openSocket(pathAndQuery: String) =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => d.unsafeRunAndForget(messages.publish1(WebSocketEvent.Open(e)))
    socket.onmessage = (e: MessageEvent) =>
      d.unsafeRunAndForget(messages.publish1(WebSocketEvent.Message(e)))
    socket.onclose = (e: CloseEvent) =>
      d.unsafeRunAndForget(messages.publish1(WebSocketEvent.Close(e)))
    socket.onerror = (e: Event) => d.unsafeRunAndForget(messages.publish1(WebSocketEvent.Error(e)))
    socket

  private def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

  def close(): Unit = socket.close()
