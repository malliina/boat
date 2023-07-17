package com.malliina.boat

import cats.syntax.show.toShow
import io.circe.*

import scala.scalajs.js.{Date, URIUtils}

object BoatSocket:
  def query(track: PathState, sample: Option[Int], from: Option[Date], to: Option[Date]): String =
    val params = track match
      case Name(name)           => Seq(TrackName.Key -> name.show)
      case Canonical(canonical) => Seq(TrackCanonical.Key -> canonical.show)
      case Route(req)           => Nil
      case Timed(from, to)      => Seq(Timings.From -> from, Timings.To -> to)
      case NoTrack              => Nil
    val timeParams =
      from.map(f => Timings.From -> f.toISOString()).toList ++
        to.map(t => Timings.To -> t.toISOString())
    val allParams = (params ++ timeParams) ++ sample.map(s => FrontKeys.SampleKey -> s"$s").toList

    val kvs = allParams.map { (k, v) => s"$k=${URIUtils.encodeURIComponent(v)}" }.mkString("&")
    if kvs.nonEmpty then s"?$kvs" else ""

abstract class BoatSocket(path: String) extends BaseSocket(path) with BaseFront:
  def this(track: PathState, sample: Option[Int]) =
    this(s"/ws/updates${BoatSocket.query(track, sample, None, None)}")

  override def handlePayload(payload: Json): Unit =
    payload.as[FrontEvent].fold(err => onJsonFailure(err, payload), consume)

  private def consume(event: FrontEvent): Unit = event match
    case ce @ CoordsEvent(coords, _) if coords.nonEmpty => onCoords(ce)
    case CoordsBatch(coords) if coords.nonEmpty         => coords.foreach(e => onCoords(e))
    case SentencesEvent(_, _)                           => ()
    case PingEvent(_, _)                                => ()
    case VesselMessages(messages)                       => onAIS(messages)
    case other                                          => log.info(s"Unknown event: '$other'.")

  def onCoords(event: CoordsEvent): Unit
  def onAIS(messages: Seq[VesselInfo]): Unit
