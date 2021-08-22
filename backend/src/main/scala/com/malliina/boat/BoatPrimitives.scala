package com.malliina.boat

import io.circe._

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object BoatPrimitives {
  implicit val durationFormat: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeDouble.map(_.seconds),
    Encoder.encodeDouble.contramap(_.toSeconds.toDouble)
  )
}
