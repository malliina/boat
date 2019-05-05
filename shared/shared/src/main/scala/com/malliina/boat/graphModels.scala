package com.malliina.boat

import com.malliina.measure.DistanceM
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Json, Reads, Writes}

import scala.concurrent.duration.FiniteDuration

object DurationDoubleFormat {
  import concurrent.duration.DurationDouble
  implicit val json: Format[FiniteDuration] = Format[FiniteDuration](
    Reads(_.validate[Double].map(_.seconds)),
    Writes(d => toJson(1.0d * d.toMillis / 1000))
  )
}

case class Link(to: Coord, cost: DistanceM)

object Link {
  implicit val json = Json.format[Link]
}

case class RouteSpec(links: List[Link], cost: DistanceM) {
  def coords = links.map(_.to)

  def finish(from: Coord, to: Coord, duration: FiniteDuration) =
    RouteResult(from, to, this, duration)
}

object RouteSpec {
  val Cost = "cost"
  implicit val df = DurationDoubleFormat.json
  implicit val json = Json.format[RouteSpec]
}

case class RouteResult(from: Coord, to: Coord, route: RouteSpec, duration: FiniteDuration)

object RouteResult {
  implicit val df: Format[FiniteDuration] = DurationDoubleFormat.json
  implicit val json = Json.format[RouteResult]
}
