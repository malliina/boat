package com.malliina.boat

import play.api.libs.json.JsValue

abstract class BoatSocket(path: String) extends BaseSocket(path) with BaseFront {
  override def handlePayload(payload: JsValue): Unit =
    payload.validate[FrontEvent].map(consume).recover { case err => onJsonFailure(err) }

  def consume(event: FrontEvent): Unit = event match {
    case ce@CoordsEvent(coords, _) if coords.nonEmpty => onCoords(ce)
    case CoordsBatch(coords) if coords.nonEmpty => coords.foreach(e => onCoords(e))
    case SentencesEvent(_, _) => ()
    case PingEvent(_) => ()
    case other => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(event: CoordsEvent): Unit
}
