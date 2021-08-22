package com.malliina.boat

import io.circe._

object BoatSocket {
  def query(track: PathState, sample: Option[Int]): String = {
    val params = track match {
      case Name(name)           => Seq(TrackName.Key -> name.name)
      case Canonical(canonical) => Seq(TrackCanonical.Key -> canonical.name)
      case Route(req)           => Nil
      case NoTrack              => Nil
    }
    val allParams = params ++ sample.map(s => FrontKeys.SampleKey -> s"$s").toList
    val kvs = allParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    if (kvs.nonEmpty) s"?$kvs" else ""
  }

}

abstract class BoatSocket(path: String) extends BaseSocket(path) with BaseFront {
  def this(track: PathState, sample: Option[Int]) =
    this(s"/ws/updates${BoatSocket.query(track, sample)}")

  override def handlePayload(payload: Json): Unit =
    payload.as[FrontEvent].fold(err => onJsonFailure(err, payload), consume)

  def consume(event: FrontEvent): Unit = event match {
    case ce @ CoordsEvent(coords, _) if coords.nonEmpty     => onCoords(ce)
    case gce @ GPSCoordsEvent(coords, _) if coords.nonEmpty => onGps(gce)
    case CoordsBatch(coords) if coords.nonEmpty             => coords.foreach(e => onCoords(e))
    case SentencesEvent(_, _)                               => ()
    case PingEvent(_)                                       => ()
    case VesselMessages(messages)                           => onAIS(messages)
    case other                                              => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(event: CoordsEvent): Unit
  def onGps(event: GPSCoordsEvent): Unit
  def onAIS(messages: Seq[VesselInfo]): Unit
}
