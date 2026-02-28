package com.malliina.boat.graph

import cats.effect.Sync
import com.malliina.boat.*
import com.malliina.boat.graph.Graph.{intersection, log}
import com.malliina.geo.{Coord, CoordHash, Latitude, Longitude}
import com.malliina.measure.DistanceM
import com.malliina.util.AppLogger
import io.circe.*
import io.circe.parser.decode

import java.nio.file.Files
import scala.annotation.tailrec
import scala.concurrent.duration.DurationLong

object Graph:
  val log = AppLogger(getClass)

  given Codec[Graph] = Codec.from(
    Decoder[List[ValueNode]].map(fromNodes),
    Encoder[List[ValueNode]].contramap(g => g.toList.toList)
  )
  private val graphLocalFile = LocalConf.appDir.resolve("vaylat-all.json")
  private def graphFile = Resources.file("vaylat-all.json", graphLocalFile)

  def load[F[_]: Sync]: F[Graph] =
    val F = Sync[F]
    F.rethrow(F.delay(decode[Graph](Files.readString(graphFile))))

  def apply(edges: List[Edge]): Graph =
    val g = Graph()
    g.edges(edges)

  def nodes(nodes: Seq[ValueNode]): Graph =
    Graph(nodes.map(n => n.from.hash -> n).toMap)

  def apply(nodes: Map[CoordHash, ValueNode] = Map.empty): Graph =
    new Graph(nodes)

  def intersection(line1: Line, line2: Line): Option[Coord] =
    val parsed = for
      l1c <- line1.c
      l1d <- line1.d
      l2c <- line2.c
      l2d <- line2.d
      if math.abs(l2d - l1d) < 0.001
    yield (l1c, l1d, l2c, l2d)
    parsed.flatMap: (l1c, l1d, l2c, l2d) =>
      val x = (l2c - l1c) / (l1d - l2d)
      val y = (l2d * l1c - l1d * l2c) / (l2d - l1d)
      for
        lng <- Longitude.build(x).toOption
        lat <- Latitude.build(y).toOption
        cross = Coord(lng, lat)
        if line1.boxContains(cross) && line2.boxContains(cross)
      yield cross

  def fromList(es: List[ValueEdge]): Graph =
    apply(
      es.groupBy(_.from.hash)
        .map: (k, ves) =>
          k -> ValueNode(ves.head.from, ves.map(_.link))
    )

  def fromNodes(ns: List[ValueNode]) =
    apply(ns.map(n => n.from.hash -> n).toMap)

class Graph(val nodes: Map[CoordHash, ValueNode]):
  def toList = nodes.values

  def coords = toList.flatMap(_.edges).flatMap(es => Seq(es.from, es.to)).toSet

  def isEmpty = coords.isEmpty

  def nearest(to: Coord): Coord =
    coords.minBy(c => Earth.distance(c, to))

  def edges(es: List[Edge]): Graph = es.foldLeft(this)((acc, e) => acc.edge(e))

  def contains(edge: Edge): Boolean =
    contains(edge.from, edge.to) || contains(edge.to, edge.from)

  private def contains(from: Coord, to: Coord): Boolean =
    nodes.get(from.hash).exists(_.links.exists(_.to.hash == to.hash))

  /** Adds `edge` to the graph. If `edge` intersects another edge, a node is added at the crossing
    * along with four edges: to/from the endpoints of `edge` and to/from the endpoints of the
    * intersected edge.
    *
    * @param edge
    *   edge to add
    * @return
    *   a new graph with the edge added
    */
  private def edge(edge: Edge): Graph =
    if contains(edge) then this
    else
      val crossingEdges =
        nodes.values
          .flatMap(_.edges)
          .toList
          .filter(e => !e.isConnected(edge))
          .flatMap: existingEdge =>
            intersection(edge.line, existingEdge.line).toList
              .filter(c => !edge.contains(c))
              .flatMap: crossing =>
                val withCrossing =
                  List(Edge(edge.from, crossing), Edge(edge.to, crossing))
                val withExisting =
                  if !existingEdge.contains(crossing) then
                    List(
                      Edge(existingEdge.from, crossing),
                      Edge(existingEdge.to, crossing)
                    )
                  else Nil
                withCrossing ++ withExisting
      if crossingEdges.isEmpty then
        val valuedLink = Link(edge.to, cost(edge.from, edge.to))
        val valued = ValueEdge(edge.from, valuedLink.to, valuedLink.cost)
        val existing = nodes.get(edge.from.hash)
        val isNewEdge =
          !existing.map(_.edges).exists(_.exists(_.isSimilar(valued)))
        val toHash = edge.to.hash
        val withTo = nodes.updated(
          toHash,
          nodes
            .getOrElse(toHash, ValueNode(edge.to, Nil))
            .link(Link(edge.from, valuedLink.cost))
        )
        val withToAndFrom =
          if existing.isEmpty || isNewEdge then
            val ret = withTo.updated(
              edge.from.hash,
              existing
                .map(_.link(valuedLink))
                .getOrElse(ValueNode(edge.from, List(valuedLink)))
            )
            ret
          else withTo
        Graph(withToAndFrom)
      else edges(crossingEdges)

  def shortest(from: Coord, to: Coord): Either[GraphError, RouteResult] =
    val startMillis = System.currentTimeMillis()
    log.info(s"Finding shortest route from $from to $to...")
    val start =
      nodes
        .get(from.hash)
        .orElse(nodes.get(nearest(from).hash))
        .toRight(UnresolvedFrom(from))
    val end = nodes.get(to.hash).map(_.from).getOrElse(nearest(to))
    for
      startNode <- start
      _ = log.debug(s"Starting from ${startNode.from} and ending at $end...")
      initialPaths =
        startNode.links.map(link => ValueRoute(link, Link(startNode.from, DistanceM.zero) :: Nil))
      result <- search(startNode.from, end, initialPaths, Map.empty)
        .map(_.reverse)
    yield
      val duration = (System.currentTimeMillis() - startMillis).millis
      val totalCost =
        result.links.headOption
          .fold(DistanceM.zero)(head => cost(from, head.to)) +
          result.cost +
          result.links.lastOption
            .fold(DistanceM.zero)(last => cost(last.to, to))
      val totalFormatted = formatDistance(totalCost)
      val routeFormatted = formatDistance(result.cost)
      log.info(
        s"Found shortest route from $from to $to with route length $routeFormatted and total length $totalFormatted in $duration."
      )
      result.finish(from, to, totalCost, duration)

  def formatDistance(d: DistanceM) = "%.3f km".format(d.toKilometers)

  @tailrec
  private def search(
    from: Coord,
    to: Coord,
    currentPaths: List[ValueRoute],
    shortestKnown: Map[CoordHash, ValueRoute]
  ): Either[GraphError, ValueRoute] =
    val candidates = shortestKnown
      .get(to.hash)
      .map(hit => currentPaths.filter(_.cost < hit.cost))
      .getOrElse(currentPaths)
    val nextLevel = candidates.flatMap: path =>
      nodes.get(path.to.hash).toList.flatMap(edges => edges.links.map(link => link :: path))
    if nextLevel.isEmpty then
      log.info("Search complete, graph exhausted.")
      shortestKnown.get(to.hash).toRight(NoRoute(from, to))
    else
      val candidateLevel = nextLevel.filter: route =>
        val hash = route.to.hash
        !shortestKnown.contains(hash) || shortestKnown
          .get(hash)
          .exists(_.cost > route.cost)
      val novel =
        candidateLevel.groupBy(_.to.hash).view.mapValues(_.minBy(_.cost))
      val novelList = novel.values.toList
      val (hits, others) = novelList.partition(_.to.hash == to.hash)
      val newShortest = shortestKnown ++ novel
      val shortest = newShortest.get(to.hash)
      // Hacking if/else in order to maintain tailrec
      if shortest.isDefined && others.forall(_.cost >= shortest.get.cost) then
        log.debug(s"Found shortest path from $from to $to.")
        Right(shortest.get)
      else
        if hits.nonEmpty && others.nonEmpty then
          val hitCost = hits.minBy(_.cost).cost
          val otherCost = others.minBy(_.cost).cost
          log.debug(
            s"Found path from $from to $to with cost $hitCost. Another path exists with cost $otherCost, therefore continuing search..."
          )
        search(from, to, novelList, newShortest)

  private def cost(from: Coord, to: Coord): DistanceM = Earth.distance(from, to)
