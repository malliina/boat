package com.malliina.boat.graph

import java.nio.file.{Files, Paths}

import com.malliina.boat._
import com.malliina.boat.graph.Graph.{intersection, log}
import com.malliina.measure.DistanceM
import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, Json, Reads, Writes}

import scala.annotation.tailrec
import scala.collection.parallel.ParSeq
import com.malliina.measure.DistanceIntM

object Graph {
  val log = LoggerFactory.getLogger(getClass)

  implicit val writer: Format[Graph] = Format[Graph](
    Reads[Graph](json => json.validate[List[ValueNode]].map(fromNodes)),
    Writes[Graph](g => Json.toJson(g.toList))
  )
  val graphFile = Paths.get(
    getClass.getClassLoader.getResource("com/malliina/boat/graph/vaylat-all.json").getFile)
  lazy val all = Json.parse(Files.readAllBytes(graphFile)).as[Graph]

  def apply(edges: List[Edge]): Graph = {
    val g = Graph()
    g.edges(edges)
  }

  def nodes(nodes: Seq[ValueNode]): Graph =
    apply(nodes.map { n =>
      n.from.hash -> n
    }.toMap)

  def apply(nodes: Map[CoordHash, ValueNode] = Map.empty): Graph =
    new Graph(nodes)

  def intersection(line1: Line, line2: Line): Option[Coord] = {
    if (math.abs(line2.d - line1.d) < 0.001) {
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

  def fromList(es: List[ValueEdge]): Graph = {
    apply(es.groupBy(_.from.hash).mapValues { ves =>
      ValueNode(ves.head.from, ves.map(_.link))
    })
  }

  def fromNodes(ns: List[ValueNode]) =
    apply(ns.map { n =>
      n.from.hash -> n
    }.toMap)
}

class Graph(val nodes: Map[CoordHash, ValueNode]) {
  def toList = nodes.values

  def coords = toList.flatMap(_.edges).flatMap(es => Seq(es.from, es.to)).toSet

  def isEmpty = coords.isEmpty

//  def nearest(to: Coord): Either[GraphError, Coord] =
//    if (isEmpty) Left(EmptyGraph)
//    else Right(coords.minBy(c => Earth.distance(c, to)))

  def nearest(to: Coord): Coord =
    coords.minBy(c => Earth.distance(c, to))

  def edges(es: List[Edge]): Graph = es.foldLeft(this) { (acc, e) =>
    acc.edge(e)
  }

  def contains(edge: Edge): Boolean = contains(edge.from, edge.to) || contains(edge.to, edge.from)

  private def contains(from: Coord, to: Coord): Boolean =
    nodes.get(from.hash).exists(_.links.exists(_.to.hash == to.hash))

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
      val crossingEdges =
        nodes.values.flatMap(_.edges).toList.filter(e => !e.isConnected(edge)).flatMap {
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
                withCrossing ++ withExisting
              }
        }
      if (crossingEdges.isEmpty) {
        val valuedLink = Link(edge.to, cost(edge.from, edge.to))
        val valued = valuedLink.from(edge.from)
        val existing = nodes.get(edge.from.hash)
        val isNewEdge = !existing.map(_.edges).exists(_.exists(_.isSimilar(valued)))
        val toHash = edge.to.hash
        val withTo = nodes.updated(
          toHash,
          nodes.getOrElse(toHash, ValueNode(edge.to, Nil)).link(Link(edge.from, valuedLink.cost)))
        val withToAndFrom =
          if (existing.isEmpty || isNewEdge) {
            val ret = withTo.updated(
              edge.from.hash,
              existing.map(_.link(valuedLink)).getOrElse(ValueNode(edge.from, List(valuedLink))))
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

  def shortest(from: Coord, to: Coord): Either[GraphError, RouteResult] = {
    log.info(s"Finding shortest route from $from to $to...")
    val start =
      nodes.get(from.hash).orElse(nodes.get(nearest(from).hash)).toRight(UnresolvedFrom(from))
    val end = nodes.get(to.hash).map(_.from).getOrElse(nearest(to))
    for {
      startNode <- start
      _ = log.info(s"Starting from ${startNode.from} and ending at $end...")
      initialPaths = startNode.links.map(link =>
        ValueRoute(link :: Link(startNode.from, DistanceM.zero) :: Nil))
      result <- search(startNode.from, end, initialPaths, Map.empty).map(_.reverse)
    } yield result.finish(from, to)
  }

  @tailrec
  private def search(from: Coord,
                     to: Coord,
                     currentPaths: List[ValueRoute],
                     shortestKnown: Map[CoordHash, ValueRoute]): Either[GraphError, ValueRoute] = {
//    val candidates = currentPaths.filter(r => r.to.forall(t => shortestKnown.get(t.hash).forall(_.cost > r.cost)))
    val candidates = shortestKnown.get(to.hash).map(hit => currentPaths.filter(_.cost < hit.cost)).getOrElse(currentPaths)
    //    log.debug(s"Searching with paths of cost ${currentPaths.map(_.cost).mkString(", ")}...")
    val nextLevel = candidates.flatMap { path =>
      path.to.toList.flatMap { leaf =>
        nodes.get(leaf.hash).toList.flatMap { edges =>
          edges.links.map { link =>
            link :: path
          }
        }
      }
    }
    if (nextLevel.isEmpty) {
      log.info("Search complete, graph exhausted.")
      shortestKnown.get(to.hash).toRight(NoRoute(from, to))
    } else {
      val candidateLevel = nextLevel.filter { route =>
        route.to.exists { leaf =>
          val hash = leaf.hash
          !shortestKnown.contains(hash) || shortestKnown.get(hash).exists(_.cost > route.cost)
        }
      }
      val novel = candidateLevel.flatMap { route =>
        route.to.map { coord =>
          coord.hash -> route
        }
      }.toMap
      val (hits, others) = candidateLevel.partition(_.to.exists(_.hash == to.hash))
      val newShortest = shortestKnown ++ novel
      val shortest = shortestKnown.get(to.hash)
      // Hacking if/else in order to maintain tailrec
      if (shortest.isDefined && others.forall(_.cost >= shortest.get.cost)) {
        log.info(s"Found shortest path from $from to $to.")
        Right(shortest.get)
      } else {
        if (hits.nonEmpty && others.nonEmpty) {
          val otherCost = others.minBy(_.cost).cost
          log.info(
            s"Found path from $from to $to with cost ${hits.minBy(_.cost).cost}. Another path exists with cost $otherCost, therefore continuing search...")
        }
        search(from, to, candidateLevel, newShortest)
      }
    }
  }

//  @tailrec
//  private def search(from: Coord,
//                     to: Coord,
//                     currentPaths: List[ValueRoute],
//                     shortestKnown: Map[CoordHash, ValueRoute]): Either[GraphError, ValueRoute] = {
//    val candidates = currentPaths
////      shortestKnown.get(to.hash).map(hit => currentPaths.filter(_.cost < hit.cost)).getOrElse(currentPaths)
////    log.info(s"Searching with ${candidates.size} paths of cost ${candidates.map(_.cost).mkString(", ")}...")
//    log.info(s"Searching ${candidates.size} paths having ${shortestKnown.size} known routes...")
//    val nextLevel = candidates.flatMap { path =>
//      path.to.toList.flatMap { leaf =>
//        nodes.get(leaf.hash).toList.flatMap { edges =>
//          edges.links.filter { l =>
//            shortestKnown.get(l.to.hash).forall(s => s.cost > (l :: path).cost)
//          }.map { link =>
//            link :: path
//          }
//        }
//      }
//    }
//    if (nextLevel.isEmpty) {
//      log.info("Search complete, graph exhausted.")
//      shortestKnown.get(to.hash).toRight(NoRoute(from, to))
//    } else {
//      val candidateLevel = nextLevel.filter { route =>
//        route.edges.headOption.exists { leaf =>
//          !shortestKnown.contains(leaf.to.hash) || shortestKnown.get(leaf.to.hash).exists(_.cost > route.cost)
//        }
//      }
//      val novel = candidateLevel.flatMap { route =>
//        route.to.map { coord =>
//          coord.hash -> route
//        }
//      }.toMap
//      val (hits, others) = candidateLevel.par.partition(_.to.exists(_.hash == to.hash))
//      val newShortest = shortestKnown ++ novel
//      val shortest = newShortest.get(to.hash)
//      // Hacking if/else in order to maintain tailrec
//      if (shortest.isDefined && others.forall(_.cost >= shortest.get.cost)) {
//        log.info(s"Found shortest path from $from to $to.")
//        Right(shortest.get)
//      } else {
//        if (hits.nonEmpty && others.nonEmpty) {
//          val otherCost = others.minBy(_.cost).cost
//          log.info(
//            s"Found path from $from to $to with cost ${hits.minBy(_.cost).cost}. Another path exists with cost $otherCost, therefore continuing search...")
//        }
////        log.info(s"Next level with ${candidateLevel.size} candidates")
//        search(from, to, candidateLevel, newShortest)
//      }
//    }
//  }

  private def cost(from: Coord, to: Coord): DistanceM = Earth.distance(from, to)
}