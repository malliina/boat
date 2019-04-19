package com.malliina.boat.shapes

import java.io.FileInputStream
import java.nio.file.Paths

import com.malliina.boat.{
  Coord,
  Earth,
  FeatureCollection,
  Latitude,
  LineGeometry,
  Longitude,
  MultiLineGeometry
}
import org.scalatest.FunSuite
import play.api.libs.json.Json
import com.malliina.measure.DistanceIntM
import scala.annotation.tailrec

class GraphTests extends FunSuite {
  ignore("create graph from geojson") {
    fromResource("vaylat-geo.json")
  }

  ignore("nearest") {
    val graph = fromResource("vaylat-mini.json")
    val n = graph.nearest(Coord(Longitude(24), Latitude(60)))
    println(graph.nodes)
    println(n)
  }

  test("haversine") {
    val helsinki = Coord(Longitude(24.9384), Latitude(60.1699))
    val porkkala = Coord(Longitude(24.4001), Latitude(59.9814))
    val actual = Earth.distance(helsinki, porkkala)
    assert(actual >= 36.kilometers)
    assert(actual <= 37.kilometers)
  }

  def fromResource(filename: String) = {
    val filePath = s"com/malliina/boat/shapes/$filename"
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
