package com.malliina.boat.graph

import com.malliina.boat.Coord
import com.malliina.measure.DistanceM
import play.api.libs.json.Json

case class Line(from: Coord, to: Coord) {
  def x1 = from.lng.lng
  def y1 = from.lat.lat
  def x2 = to.lng.lng
  def y2 = to.lat.lat
  val d = (y2 - y1) / (x2 - x1)
  val c = y1 - x1 * d

  def boxContains(coord: Coord): Boolean = {
    val x = coord.lng.lng
    val withinX = (x >= x1 && x <= x2) || (x <= x1 && x >= x2)
    val y = coord.lat.lat
    val withinY = (y >= y1 && y <= y2) || (x <= y1 && y >= y2)
    withinX && withinY
  }
}

trait EdgeLike {
  def from: Coord
  def to: Coord
  def line = Line(from, to)
  def contains(coord: Coord): Boolean = from.hash == coord.hash || to.hash == coord.hash
  def isConnected(other: EdgeLike) = contains(other.from) || contains(other.to)
  def isSimilar(other: EdgeLike) = contains(other.from) && contains(other.to)
  def describe = s"(${from.hash} - ${to.hash})"
}

case class Link(to: Coord, cost: DistanceM) {
  def from(coord: Coord) = ValueEdge(coord, to, cost)
}

object Link {
  implicit val json = Json.format[Link]
}

case class Edge(from: Coord, to: Coord) extends EdgeLike

case class ValueEdge(from: Coord, to: Coord, cost: DistanceM) extends EdgeLike {
  def link = Link(to, cost)
}

object ValueEdge {
  implicit val json = Json.format[ValueEdge]
}

case class ValueRoute(edges: List[Link]) extends AnyVal {
  def cost = DistanceM(edges.map(_.cost.toMeters).sum)
  def ::(next: Link) = ValueRoute(next :: edges)
  def to = edges.headOption.map(_.to)
  def reverse = ValueRoute(edges.reverse)
  def finish(from: Coord, to: Coord) = RouteResult(from, to, reverse)
  def coords = edges.map(_.to)
}

case class RouteResult(from: Coord, to: Coord, route: ValueRoute)

case class ValueNode(from: Coord, links: List[Link]) {
  def edges = links.map { link => link.from(from) }
  def link(l: Link) = ValueNode(from, l :: links)
}

object ValueNode {
  implicit val json = Json.format[ValueNode]
}
