package com.malliina.boat.shapes

import com.malliina.boat.shapes.Graph.intersection
import com.malliina.boat.{Coord, CoordHash, Earth, Latitude, Longitude}

import scala.annotation.tailrec

object Graph {
  def apply(edges: List[Edge]): Graph = {
    val g = Graph()
    g.edges(edges)
  }

  def apply(nodes: Map[CoordHash, List[ValueEdge]] = Map.empty): Graph =
    new Graph(nodes)

  def intersection(line1: Line, line2: Line): Option[Coord] = {
    if (line2.d - line1.d < 0.001) {
      None
    } else {
      val x = (line2.c - line1.c) / (line1.d - line2.d)
      val y = (line2.d * line1.c - line1.d * line2.c) / (line2.d - line1.d)
      val cross = Coord(Longitude(x), Latitude(y))
      if (line1.boxContains(cross) && line2.boxContains(cross))
        Option(cross)
      else
        None
    }
  }
}

class Graph(val nodes: Map[CoordHash, List[ValueEdge]]) {
  def coords = nodes.values.flatten.flatMap(es => Seq(es.from, es.to)).toSet

  def nearest(to: Coord) = coords.minBy(c => Earth.distance(c, to))

  def edges(es: List[Edge]): Graph = es.foldLeft(this) { (acc, e) =>
    acc.edge(e)
  }

  def contains(edge: Edge): Boolean = contains(edge.from, edge.to) || contains(edge.to, edge.from)

  private def contains(from: Coord, to: Coord): Boolean =
    nodes.get(from.hash).exists(_.exists(_.to.hash == to.hash))

  /** Adds `edge` to the graph. If `edge` intersects another edge, a node is added at the crossing
    * along with four edges: to/from the endpoints of `edge` and to/from the endpoints of the
    * intersected edge.
    *
    * @param edge edge to add
    * @return a new graph with the edge added
    */
  def edge(edge: Edge): Graph = {
    if (contains(edge)) {
      this
    } else {
      val crossingEdges = nodes.values.flatten.toList.filter(e => !e.isConnected(edge)).flatMap {
        existingEdge =>
          intersection(edge.line, existingEdge.line).toList
            .filter(c => !edge.contains(c))
            .flatMap { crossing =>
              val withCrossing = List(
                Edge(edge.from, crossing),
                Edge(edge.to, crossing)
              )
              val withExisting =
                if (!existingEdge.contains(crossing)) {
                  List(
                    Edge(existingEdge.from, crossing),
                    Edge(existingEdge.to, crossing)
                  )
                } else {
                  Nil
                }
              val ret = withCrossing ++ withExisting
//            println(s"${existingEdge.contains(crossing)} for (${existingEdge.from.hash}, ${existingEdge.to.hash}) contains (${crossing.hash})")
              println(
                s"${ret.length} edges from crossing at ${crossing.hash} between ${edge.describe} and ${existingEdge.describe}")
              ret
            }
      }
      if (crossingEdges.isEmpty) {
        val valued = ValueEdge(edge.from, edge.to, cost(edge.from, edge.to))
        val existing = nodes.get(edge.from.hash)
        val isNewEdge = !existing.exists(_.exists(_.isSimilar(valued)))
        val withTo =
          if (nodes.contains(edge.to.hash)) nodes
          else nodes.updated(edge.to.hash, Nil)
        val withToAndFrom =
          if (existing.isEmpty || isNewEdge) {
            val ret = withTo.updated(edge.from.hash, valued :: existing.getOrElse(Nil))
            println(s"adding $valued to coordinate with ${existing.getOrElse(Nil).length} existing edges")
            ret
          } else {
            withTo
          }
        Graph(withToAndFrom)
      } else {
        edges(crossingEdges)
      }
    }
  }

  def shortest(from: Coord, to: Coord): Option[ValueRoute] = {
    val start = nodes.get(from.hash).orElse(nodes.get(nearest(from).hash))
    val end = nodes.get(to.hash).flatMap(_.headOption.map(_.from)).getOrElse(nearest(to))
    start
      .map { edges =>
        edges.map(edge => ValueRoute(List(edge)))
      }
      .flatMap { paths =>
        search(from, end, paths, Map.empty).map(_.reverse)
      }
  }


  @tailrec
  private def search(from: Coord,
                     to: Coord,
                     currentPaths: List[ValueRoute],
                     shortestKnown: Map[Coord, ValueRoute]): Option[ValueRoute] = {
    val nextLevel = currentPaths.flatMap { path =>
      path.edges.headOption.toList.flatMap { leaf =>
        nodes.get(leaf.to.hash).toList.flatMap { edges =>
          edges.map { edge =>
            edge :: path
          }
        }
      }
    }
    if (nextLevel.isEmpty) {
      shortestKnown.get(to)
    } else {
      val candidateLevel = nextLevel.filter { route =>
        route.edges.headOption.exists { leaf =>
          !shortestKnown.contains(leaf.to) || shortestKnown.get(leaf.to).exists(_.cost > route.cost)
        }
      }
      val (hits, others) = candidateLevel.partition(_.to.contains(to))
      val novel = candidateLevel.flatMap { route =>
        route.to.map { coord =>
          coord -> route
        }
      }.toMap
      val newShortest = shortestKnown ++ novel
      if (hits.nonEmpty) {
        val shortest = (hits ++ newShortest.get(to).toList).minBy(_.cost)
        if (others.forall(_.cost >= shortest.cost)) Option(shortest)
        else search(from, to, candidateLevel, newShortest)
      } else {
        search(from, to, candidateLevel, newShortest)
      }
    }
  }

  private def cost(from: Coord, to: Coord): Double = Earth.distance(from, to).toMeters
}
