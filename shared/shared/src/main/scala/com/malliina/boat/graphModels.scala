package com.malliina.boat

import com.malliina.measure.DistanceM
import io.circe.*
import io.circe.generic.semiauto.*
import scala.concurrent.duration.FiniteDuration

object DurationDoubleFormat:
  import concurrent.duration.DurationDouble
  implicit val json: Codec[FiniteDuration] = Codec.from[FiniteDuration](
    Decoder.decodeDouble.map(_.seconds),
    Encoder.encodeDouble.contramap(fd => 1.0d * fd.toMillis / 1000)
  )

case class Link(to: Coord, cost: DistanceM)

object Link:
  implicit val json: Codec[Link] = deriveCodec[Link]

case class RouteSpec(links: List[Link], cost: DistanceM):
  def coords = links.map(_.to)

  def finish(from: Coord, to: Coord, totalCost: DistanceM, duration: FiniteDuration) =
    RouteResult(from, to, this, totalCost, duration)

  def ::(link: Link) = RouteSpec(link :: links, link.cost + cost)

object RouteSpec:
  val Cost = "cost"
  implicit val df: Codec[FiniteDuration] = DurationDoubleFormat.json
  implicit val json: Codec[RouteSpec] = deriveCodec[RouteSpec]

case class RouteResult(
  from: Coord,
  to: Coord,
  route: RouteSpec,
  totalCost: DistanceM,
  duration: FiniteDuration
)

object RouteResult:
  implicit val df: Codec[FiniteDuration] = DurationDoubleFormat.json
  implicit val json: Codec[RouteResult] = deriveCodec[RouteResult]
