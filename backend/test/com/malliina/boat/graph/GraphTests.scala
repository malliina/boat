package com.malliina.boat.graph

import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import java.util.concurrent.{Executors, TimeUnit}

import com.malliina.boat._
import com.malliina.measure.{DistanceDoubleM, DistanceIntM}
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class GraphTests extends FunSuite {
  test("find route") {
    val from = 19.97655 lngLat 60.27473
    val to = 20.01310 lngLat 60.24488
    val graph = fromResource("vaylat-mini.json")
    val route = graph.shortest(from, to)
    assert(route.isRight)
  }

  test("route through crossing") {
    1.0 lngLat 50.4
    val from = 24.8438 lngLat 60.1395
    val to = 24.83144 lngLat 60.09777
    val graph = fromResource("vaylat-crossing.json")
    val route = graph.shortest(from, to)
    assert(route.isRight)
  }

  test("read, write, read and use graph") {
    val from = 24.8438 lngLat 60.1395
    val to = 24.83144 lngLat 60.09777
    val graph = fromResource("vaylat-crossing.json")
    assert(!graph.isEmpty)
    val route = graph.shortest(from, to)
    val json = Json.toJson(graph.toList)
    val route2 = Graph.fromNodes(json.as[List[ValueNode]]).shortest(from, to)
    assert(route.isRight)
    assert(route2.isRight)
    val r1 = route.right.get
    val r2 = route.right.get
    assert(r1.route.coords.size === r2.route.coords.size)
    assert(r1.route.cost - r2.route.cost < 0.1.meters)
  }

  test("haversine") {
    val helsinki = 24.9384 lngLat 60.1699
    val porkkala = 24.4001 lngLat 59.9814
    val actual = Earth.distance(helsinki, porkkala)
    assert(actual >= 36.kilometers)
    assert(actual <= 37.kilometers)
  }

  test("read geo file") {
    val graph = Graph.all
//    val from = 24.37521 lngLat 59.97423
    val from = 24.8438 lngLat 60.1395
    val to = 25.2130 lngLat 60.1728
    val exec = Executors.newCachedThreadPool()
    val ec = ExecutionContext.fromExecutor(exec)
    val route = try {
      Await.result(Future(graph.shortest(from, to))(ec), 20.seconds)
    } finally {
      exec.shutdownNow()
      exec.awaitTermination(10, TimeUnit.SECONDS)
    }
    assert(route.isRight)
    val r = route.right.get
    val cost = r.route.cost
    println(Json.toJson(toGeo(r)))
    assert(cost > 22.kilometers)
    assert(cost < 22.8.kilometers)
  }

  ignore("read geo file 2") {
    val graph = Graph.all
    val from = 24.37521 lngLat 59.97423
    //    val from = 24.8438 lngLat 60.1395
    val to = 25.2130 lngLat 60.1728
    val exec = Executors.newCachedThreadPool()
    val ec = ExecutionContext.fromExecutor(exec)
    val route = try {
      Await.result(Future(graph.shortest(from, to))(ec), 30.seconds)
    } finally {
      exec.shutdownNow()
      exec.awaitTermination(10, TimeUnit.SECONDS)
    }
    assert(route.isRight)
    val r = route.right.get
    val cost = r.route.cost
    println(Json.toJson(toGeo(r)))
//    assert(cost > 22.kilometers)
//    assert(cost < 24.kilometers)
  }

  ignore("read and write graph") {
    val g = fromResource("vaylat-geo.json")
    Files.write(Paths.get("vaylat-all2.json"), Json.toBytes(Json.toJson(g)))
  }

  ignore("create graph from geojson") {
    fromResource("vaylat-geo.json")
  }

  ignore("nearest") {
    val graph = fromResource("vaylat-mini.json")
    val n = graph.nearest(24 lngLat 60)
    println(graph.nodes)
    println(n)
  }

  def toGeo(r: RouteResult) = FeatureCollection(
    List(Feature.line(r.route.coords), Feature.point(r.from), Feature.point(r.to)))

  def fromResource(filename: String) = {
    val filePath = s"com/malliina/boat/graph/$filename"
    val file = Paths.get(getClass.getClassLoader.getResource(filePath).getFile)
    val json = Json.parse(new FileInputStream(file.toFile))
    val es = json.as[FeatureCollection].features.flatMap { f =>
      f.geometry match {
        case MultiLineGeometry(_, coordinates) => coordinates.flatMap(edges)
        case LineGeometry(_, coordinates)      => edges(coordinates)
        case _                                 => Nil
      }
    }
    Graph(es.toList)
  }

  def edges(coords: Seq[Coord]) = edgesAcc(coords.reverse.toList, Nil)

  @tailrec
  private def edgesAcc(coords: List[Coord], acc: List[Edge]): List[Edge] =
    coords match {
      case f :: s :: tail => edgesAcc(s :: tail, Edge(f, s) :: acc)
      case _              => acc
    }
}