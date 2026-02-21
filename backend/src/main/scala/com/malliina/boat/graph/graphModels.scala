package com.malliina.boat.graph

import com.malliina.boat.{Earth, Link, RouteSpec}
import com.malliina.geo.Coord
import com.malliina.measure.DistanceM
import io.circe.*

case class Line(from: Coord, to: Coord):
  def x1 = from.lng.lng
  def y1 = from.lat.lat
  def x2 = to.lng.lng
  def y2 = to.lat.lat
  val d = if math.abs(x2 - x1) > 0 then Option((y2 - y1) / (x2 - x1)) else None
  val c = d.map(dv => y1 - x1 * dv)

  def boxContains(coord: Coord): Boolean =
    val x = coord.lng.lng
    val withinX = (x >= x1 && x <= x2) || (x <= x1 && x >= x2)
    val y = coord.lat.lat
    val withinY = (y >= y1 && y <= y2) || (x <= y1 && y >= y2)
    withinX && withinY

trait EdgeLike:
  def from: Coord
  def to: Coord
  def line = Line(from, to)
  def contains(coord: Coord): Boolean = from.hash == coord.hash || to.hash == coord.hash
  def isConnected(other: EdgeLike) = contains(other.from) || contains(other.to)
  def isSimilar(other: EdgeLike) = contains(other.from) && contains(other.to)
  def describe = s"(${from.hash} - ${to.hash})"
  def withPoint(p: Coord) = PointOnEdge(from, to, p)

case class Edge(from: Coord, to: Coord) extends EdgeLike

case class PointOnEdge(from: Coord, to: Coord, point: Coord) extends EdgeLike:
  def distanceFrom = Earth.distance(from, point)
  def distanceTo = Earth.distance(to, point)
  def closest = if distanceFrom < distanceTo then from else to

case class RouteEndpoint(desired: Coord, pseudo: Option[Coord], closest: ValueNode)

case class ValueEdge(from: Coord, to: Coord, cost: DistanceM) extends EdgeLike
  derives Codec.AsObject:
  def link = Link(to, cost)

case class ValueRoute(head: Link, tail: List[Link]):
  val to = head.to
  val edges = head :: tail
  val cost = DistanceM(edges.map(_.cost.toMeters).sum)
  def ::(next: Link) = ValueRoute(next, head :: tail)

  def reverse =
    tail.lastOption
      .map: last =>
        RouteSpec(last :: (head :: tail.init).reverse, cost)
      .getOrElse:
        RouteSpec(head :: Nil, cost)

  def coords = edges.map(_.to)

case class ValueNode(from: Coord, links: List[Link]) derives Codec.AsObject:
  def edges = links.map(link => ValueEdge(from, link.to, link.cost))
  def link(l: Link) = ValueNode(from, l :: links)
