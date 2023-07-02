package com.malliina.boat

import com.malliina.boat.BaseSocket.Ping
import com.malliina.http.FullUrl
import org.scalajs.dom
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.{Event, MessageEvent}
import io.circe.*
import io.circe.syntax.EncoderOps
import io.circe.parser.parse

object BaseSocket:
  val EventKey = "event"
  val Ping = "ping"

class BaseSocket(wsPath: String, val log: BaseLogger = BaseLogger.console):
  val socket: dom.WebSocket = openSocket(wsPath)
  val EventKey = BaseSocket.EventKey
  val BodyKey = "body"

  def handlePayload(payload: Json): Unit = ()

  def handleValidated[T: Decoder](json: Json)(process: T => Unit): Unit =
    json.as[T].fold(err => onJsonFailure(DecodingFailure(err.message, Nil), json), process)

  private def showConnected(): Unit =
    setFeedback("Connected to socket.")

  private def showDisconnected(): Unit =
    setFeedback("Connection closed.")

  def send[T: Encoder](payload: T): Unit =
    val asString = payload.asJson.noSpaces
    socket.send(asString)

  def onMessage(msg: MessageEvent): Unit =
    val asString = msg.data.toString
    log.debug(s"Got message: $asString")
    parse(asString).fold(
      err => onJsonException(asString, err),
      json =>
        val isPing = json.hcursor.downField("EventKey").as[String].contains(Ping)
        if !isPing then handlePayload(json)
    )

  private def onConnected(e: Event): Unit = showConnected()

  private def onClosed(e: CloseEvent): Unit = showDisconnected()

  private def onError(e: Event): Unit = showDisconnected()

  private def openSocket(pathAndQuery: String) =
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) => onMessage(e)
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
