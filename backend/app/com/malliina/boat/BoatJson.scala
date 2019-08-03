package com.malliina.boat

import java.util.concurrent.TimeUnit

import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Reads, Writes}

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

object BoatJson {
  implicit val durationFormat: Format[FiniteDuration] = Format[FiniteDuration](
    Reads(_.validate[Double].map(_.seconds)),
    Writes(d => toJson(d.toUnit(TimeUnit.SECONDS)))
  )
}
