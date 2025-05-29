package com.malliina.boat

import com.malliina.measure.DistanceM
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.FiniteDuration

case class Link(to: Coord, cost: DistanceM) derives Codec.AsObject

case class RouteSpec(links: List[Link], cost: DistanceM):
  def coords = links.map(_.to)

  def finish(from: Coord, to: Coord, totalCost: DistanceM, duration: FiniteDuration) =
    RouteResult(from, to, this, totalCost, duration)

  def ::(link: Link) = RouteSpec(link :: links, link.cost + cost)

object RouteSpec:
  val Cost = "cost"
  given Codec[FiniteDuration] = BoatFormats.durationDouble
  given Codec[RouteSpec] = deriveCodec[RouteSpec]

case class RouteResult(
  from: Coord,
  to: Coord,
  route: RouteSpec,
  totalCost: DistanceM,
  duration: FiniteDuration
)

object RouteResult:
  given Codec[FiniteDuration] = BoatFormats.durationDouble
  given Codec[RouteResult] = deriveCodec[RouteResult]
