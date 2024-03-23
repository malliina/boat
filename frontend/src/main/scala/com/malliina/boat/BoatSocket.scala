package com.malliina.boat

import cats.effect.std.Dispatcher
import fs2.concurrent.Topic
import io.circe.Json
import org.scalajs.dom.MessageEvent

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

abstract class BoatSocket[F[_]](path: String, messages: Topic[F, MessageEvent], d: Dispatcher[F])
  extends BaseSocket(path, messages, d)
  with BaseFront:
  def this(
    track: PathState,
    sample: Option[Int],
    messages: Topic[F, MessageEvent],
    d: Dispatcher[F]
  ) =
    this(s"/ws/updates${BoatSocket.query(track, sample)}", messages, d)

  override def handlePayload(payload: Json): Unit =
    payload.as[FrontEvent].fold(err => onJsonFailure(err, payload), consume)

  private def consume(event: FrontEvent): Unit =
    event match
      case ce @ CoordsEvent(coords, _) if coords.nonEmpty => onCoords(ce)
      case CoordsBatch(coords) if coords.nonEmpty         => coords.foreach(e => onCoords(e))
      case SentencesEvent(_, _)                           => ()
      case PingEvent(_, _)                                => ()
      case VesselMessages(messages)                       => onAIS(messages)
      case LoadingEvent(meta)                             => onLoading(meta)
      case NoDataEvent(meta)                              => onNoData(meta)
      case other                                          => log.info(s"Unknown event: '$other'.")

  def onLoading(meta: SearchQuery): Unit = ()
  def onNoData(meta: SearchQuery): Unit = ()
  def onCoords(event: CoordsEvent): Unit
  def onAIS(messages: Seq[VesselInfo]): Unit
