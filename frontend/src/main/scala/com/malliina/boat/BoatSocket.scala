package com.malliina.boat

import play.api.libs.json.JsValue

object BoatSocket {
  def query(track: Option[TrackName], sample: Option[Int]): String = {
    val kvs = (track.map(t => TrackName.Key -> t.name) ++ sample.map(s => FrontKeys.SampleKey -> s.toString))
      .map { case (k, v) => s"$k=$v" }
      .mkString("&")
    if (kvs.nonEmpty) s"?$kvs" else ""
  }

}

abstract class BoatSocket(path: String) extends BaseSocket(path) with BaseFront {
  def this(track: Option[TrackName], sample: Option[Int]) =
    this(s"/ws/updates${BoatSocket.query(track, sample)}")

  override def handlePayload(payload: JsValue): Unit =
    payload.validate[FrontEvent].map(consume).recover { case err => onJsonFailure(err) }

  def consume(event: FrontEvent): Unit = event match {
    case ce@CoordsEvent(coords, _) if coords.nonEmpty => onCoords(ce)
    case CoordsBatch(coords) if coords.nonEmpty => coords.foreach(e => onCoords(e))
    case SentencesEvent(_, _) => ()
    case PingEvent(_) => ()
    case VesselMessages(messages) =>
      onAIS(messages)
//      log.info(s"Got ${messages.length} vessel updates.")
    case other => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(event: CoordsEvent): Unit

  def onAIS(messages: Seq[VesselInfo]): Unit
}
