package com.malliina.boat

import com.comcast.ip4s.{Host, Port}
import com.malliina.values.{ErrorMessage, Readable, UserId, error}

object ErrorConstants:
  val TokenExpiredKey = "token_expired"

object Readables:
  given device: Readable[DeviceId] = from[Long, DeviceId](DeviceId.build)
  given userId: Readable[UserId] = from[Long, UserId](UserId.build)
  given trackTitle: Readable[TrackTitle] = from[String, TrackTitle](TrackTitle.build)
  given host: Readable[Host] =
    from[String, Host](s => Host.fromString(s).toRight(s"Invalid host: '$s'.".error))
  given port: Readable[Port] =
    from[Int, Port](i => Port.fromInt(i).toRight(s"Invalid port: '$i'.".error))

  def from[T, U](build: T => Either[ErrorMessage, U])(using tr: Readable[T]): Readable[U] =
    tr.emap(t => build(t))
