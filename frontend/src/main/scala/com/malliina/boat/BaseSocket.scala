package com.malliina.boat

import com.malliina.boat.BaseSocket.Ping
import com.malliina.http.FullUrl
import org.scalajs.dom
import org.scalajs.dom.CloseEvent
import org.scalajs.dom.raw.{ErrorEvent, Event, MessageEvent}
import org.scalajs.jquery.{JQuery, jQuery}
import play.api.libs.json._

import scala.util.Try

object BaseSocket {
  val EventKey = "event"
  val Ping = "ping"
}

class BaseSocket(wsPath: String, val log: BaseLogger = BaseLogger.console) {
  val socket: dom.WebSocket = openSocket(wsPath)
  val EventKey = BaseSocket.EventKey
  val BodyKey = "body"

  def elem(id: String): JQuery = jQuery(s"#$id")

  def handlePayload(payload: JsValue): Unit = ()

  def handleValidated[T: Reads](json: JsValue)(process: T => Unit): Unit =
    json.validate[T].fold(err => onJsonFailure(JsError(err)), process)

  def showConnected(): Unit = {
    setFeedback("Connected to socket.")
  }

  def showDisconnected(): Unit = {
    setFeedback("Connection closed.")
  }

  def send[T: Writes](payload: T): Unit = {
    val asString = Json.stringify(Json.toJson(payload))
    socket.send(asString)
  }

  def onMessage(msg: MessageEvent): Unit = {
    log.info(s"Got message: ${msg.data.toString}")
    Try(Json.parse(msg.data.toString)).map { json =>
      val isPing = (json \ EventKey).validate[String].filter(_ == Ping).isSuccess
      if (!isPing) {
        handlePayload(json)
      }
    }.recover { case e => onJsonException(e) }
  }

  def onConnected(e: Event): Unit = showConnected()

  def onClosed(e: CloseEvent): Unit = showDisconnected()

  def onError(e: ErrorEvent): Unit = showDisconnected()

  def openSocket(pathAndQuery: String) = {
    val url = wsBaseUrl.append(pathAndQuery)
    val socket = new dom.WebSocket(url.url)
    socket.onopen = (e: Event) => onConnected(e)
    socket.onmessage = (e: MessageEvent) => onMessage(e)
    socket.onclose = (e: CloseEvent) => onClosed(e)
    socket.onerror = (e: ErrorEvent) => onError(e)
    socket
  }

  def wsBaseUrl: FullUrl = {
    val location = dom.window.location
    val wsProto = if (location.protocol == "http:") "ws" else "wss"
    FullUrl(wsProto, location.host, "")
  }

  def setFeedback(feedback: String) = {
    log.info(feedback)
  }

  def onJsonException(t: Throwable): Unit = {
    log error t
  }

  protected def onJsonFailure(result: JsError): Unit = {
    log info s"JSON error $result"
  }
}
