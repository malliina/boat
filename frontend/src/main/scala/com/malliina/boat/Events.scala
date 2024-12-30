package com.malliina.boat

import cats.effect.Sync
import com.malliina.boat.Events.Ping
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.parser.parse
import io.circe.{DecodingFailure, Json}

object Events:
  val Ping = "ping"

class Events[F[_]: Sync](
  socketEventsTopic: Topic[F, WebSocketEvent],
  log: BaseLogger = BaseLogger.console
):
  val socketEvents = socketEventsTopic.subscribe(10)
  val isConnected = socketEvents
    .collect:
      case WebSocketEvent.Open(_)    => true
      case WebSocketEvent.Message(_) => true
      case WebSocketEvent.Close(_)   => false
      case WebSocketEvent.Error(_)   => false
    .changes
  val connectivityLogger = isConnected.tap: connected =>
    val msg = if connected then "Connected to socket." else "Connection closed."
    log.debug(msg)
  val messages = socketEvents.collect:
    case WebSocketEvent.Message(msg) => msg
  private val payloads = messages
    .flatMap: msg =>
      val asString = msg.data.toString
      parse(asString).fold(
        err =>
          onJsonException(asString, err)
          Stream.empty
        ,
        json =>
          val isPing = json.hcursor.downField(BoatJson.EventKey).as[String].contains(Ping)
          if !isPing then Stream(json) else Stream.empty
      )
  val frontEvents = payloads.flatMap: payload =>
    payload
      .as[FrontEvent]
      .fold(
        err =>
          onJsonFailure(err, payload)
          Stream.empty
        ,
        event => Stream(event)
      )
  val coordEvents = frontEvents.flatMap:
    case ce @ CoordsEvent(coords, _) if coords.nonEmpty => Stream(ce)
    case CoordsBatch(coords) if coords.nonEmpty         => Stream.emits(coords)
    case other                                          => Stream.empty
  val vesselEvents = frontEvents.collect:
    case VesselTrailsEvent(vessels) => vessels
  val aisEvents: Stream[F, Seq[VesselInfo]] = frontEvents.collect:
    case VesselMessages(messages) => messages

  private def onJsonException(asString: String, e: io.circe.Error): Unit =
    log.info(s"JSON error for '$asString'. $e")
  protected def onJsonFailure(result: DecodingFailure, value: Json): Unit =
    log.info(s"JSON error $result")
