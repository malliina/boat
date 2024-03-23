package com.malliina.boat

import cats.effect.std.Dispatcher
import com.malliina.boat.BaseSocket.Ping
import com.malliina.http.FullUrl
import fs2.concurrent.Topic
import io.circe.*
import io.circe.parser.parse
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, Event, MessageEvent}

import scala.annotation.unused

object BaseSocket:
  val Ping = "ping"

class BaseSocket[F[_]](
  wsPath: String,
  messages: Topic[F, MessageEvent],
  d: Dispatcher[F],
  val log: BaseLogger = BaseLogger.console
):
  val socket: dom.WebSocket = openSocket(wsPath)

  def handlePayload(payload: Json): Unit = ()

  private def showConnected(): Unit =
    setFeedback("Connected to socket.")

  private def showDisconnected(): Unit =
    setFeedback("Connection closed.")

  private def onMessage(msg: MessageEvent): Unit =
    val asString = msg.data.toString
    log.debug(s"Got message: $asString")
    parse(asString).fold(
      err => onJsonException(asString, err),
      json =>
        val isPing = json.hcursor.downField(BoatJson.EventKey).as[String].contains(Ping)
        if !isPing then handlePayload(json)
    )

  private def onConnected(@unused e: Event): Unit = showConnected()

  private def onClosed(@unused e: CloseEvent): Unit = showDisconnected()

  private def onError(@unused e: Event): Unit = showDisconnected()

  private def openSocket(pathAndQuery: String) =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) =>
      onMessage(e)
      d.unsafeRunAndForget(messages.publish1(e))
    socket.onclose = (e: CloseEvent) => onClosed(e)
    socket.onerror = (e: Event) => onError(e)
    socket

  private def wsBaseUrl: FullUrl =
    val location = dom.window.location
    val wsProto = if location.protocol == "http:" then "ws" else "wss"
    FullUrl(wsProto, location.host, "")

  private def setFeedback(feedback: String): Unit =
    log.debug(feedback)

  private def onJsonException(asString: String, e: io.circe.Error): Unit =
    log.info(s"JSON error for '$asString'. $e")

  protected def onJsonFailure(result: DecodingFailure, value: Json): Unit =
    log.info(s"JSON error $result")

  def close(): Unit = socket.close()
