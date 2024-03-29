package com.malliina.boat

import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object BoatPrimitives:
  given durationFormat: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeDouble.map(_.seconds),
    Encoder.encodeDouble.contramap(_.toSeconds.toDouble)
  )
