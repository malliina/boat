package com.malliina.boat

import com.malliina.boat.BaseSocket.Ping
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.parser.parse
import io.circe.{DecodingFailure, Json}
import org.scalajs.dom.MessageEvent

class Events[F[_]](messages: Topic[F, MessageEvent], log: BaseLogger = BaseLogger.console):
  private val payloads = messages
    .subscribe(10)
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

  val aisEvents: Stream[F, Seq[VesselInfo]] = frontEvents.collect:
    case VesselMessages(messages) => messages

  private def onJsonException(asString: String, e: io.circe.Error): Unit =
    log.info(s"JSON error for '$asString'. $e")
  protected def onJsonFailure(result: DecodingFailure, value: Json): Unit =
    log.info(s"JSON error $result")
