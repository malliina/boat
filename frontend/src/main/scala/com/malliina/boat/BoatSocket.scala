package com.malliina.boat

import io.circe.Json

object BoatSocket:
  def query(track: PathState, sample: Option[Int]): String =
    val qs = QueryString.parse
    track match
      case Name(name)           => qs.set(TrackName.Key, name)
      case Canonical(canonical) => qs.set(TrackCanonical.Key, canonical)
      case Route(req)           => ()
      case NoTrack              => ()
    sample.fold(qs)(s => qs.set(FrontKeys.SampleKey, s"$s"))
    if qs.isEmpty then "" else s"?$qs"

abstract class BoatSocket(path: String) extends BaseSocket(path) with BaseFront:
  def this(track: PathState, sample: Option[Int]) =
    this(s"/ws/updates${BoatSocket.query(track, sample)}")

  override def handlePayload(payload: Json): Unit =
    payload.as[FrontEvent].fold(err => onJsonFailure(err, payload), consume)

  private def consume(event: FrontEvent): Unit =
    event match
      case ce @ CoordsEvent(coords, _) if coords.nonEmpty => onCoords(ce)
      case CoordsBatch(coords) if coords.nonEmpty         => coords.foreach(e => onCoords(e))
      case SentencesEvent(_, _)                           => ()
      case PingEvent(_, _)                                => ()
      case VesselMessages(messages)                       => onAIS(messages)
      case me @ MetaEvent(_, _)                           => onMeta(me)
      case other                                          => log.info(s"Unknown event: '$other'.")

  def onMeta(event: MetaEvent): Unit
  def onCoords(event: CoordsEvent): Unit
  def onAIS(messages: Seq[VesselInfo]): Unit
