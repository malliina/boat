package com.malliina.boat

import cats.effect.std.Dispatcher
import fs2.concurrent.Topic

object BoatSocket:
  def uri(track: PathState, sample: Option[Int]): String =
    s"/ws/updates${query(track, sample)}"

  def query(track: PathState, sample: Option[Int]): String =
    val qs = QueryString.parse
    track match
      case Name(name)           => qs.set(TrackName.Key, name)
      case Canonical(canonical) => qs.set(TrackCanonical.Key, canonical)
      case Route(req)           => ()
      case NoTrack              => ()
    sample.fold(qs)(s => qs.set(FrontKeys.SampleKey, s"$s"))
    if qs.isEmpty then "" else s"?${qs.render}"

class BoatSocket[F[_]](path: String, messages: Topic[F, WebSocketEvent], d: Dispatcher[F])
  extends BaseSocket(path, messages, d)
  with BaseFront:
  def this(
    track: PathState,
    sample: Option[Int],
    messages: Topic[F, WebSocketEvent],
    d: Dispatcher[F]
  ) =
    this(BoatSocket.uri(track, sample), messages, d)
