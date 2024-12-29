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
) extends BaseDispatcher(d):
  val socket: dom.WebSocket = openSocket(wsPath)

  private def openSocket(pathAndQuery: String) =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => messages.dispatch(WebSocketEvent.Open(e))
    socket.onmessage = (e: MessageEvent) => messages.dispatch(WebSocketEvent.Message(e))
    socket.onclose = (e: CloseEvent) => messages.dispatch(WebSocketEvent.Close(e))
    socket.onerror = (e: Event) => messages.dispatch(WebSocketEvent.Error(e))
    socket

  private def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

  def close(): Unit = socket.close()

class BaseDispatcher[F[_]](d: Dispatcher[F]):
  def dispatch[T](task: F[T]): Unit = d.unsafeRunAndForget(task)

  extension [T](topic: Topic[F, T])
    def dispatch(t: T): Unit = d.unsafeRunAndForget(topic.publish1(t))
