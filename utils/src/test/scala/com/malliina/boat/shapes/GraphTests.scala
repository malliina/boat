package com.malliina.boat.shapes

import java.io.FileInputStream
import java.nio.file.Paths

import com.malliina.boat.{Coord, FeatureCollection, LineGeometry, MultiLineGeometry}
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.annotation.tailrec

class GraphTests extends FunSuite {
  ignore("create graph from geojson") {
    val filePath = "com/malliina/boat/shapes/vaylat-geo.json"
    val file = Paths.get(getClass.getClassLoader.getResource(filePath).getFile)
    val json = Json.parse(new FileInputStream(file.toFile))
    val es = json.as[FeatureCollection].features.flatMap { f => f.geometry match {
      case MultiLineGeometry(_, coordinates) => coordinates.flatMap(edges)
      case LineGeometry(_, coordinates) => edges(coordinates)
      case _ => Nil
    }}
    val g = Graph(es.toList)
  }

  def edges(coords: Seq[Coord]) = edgesAcc(coords.reverse.toList, Nil)

  @tailrec
  private def edgesAcc(coords: List[Coord], acc: List[Edge]): List[Edge] =
    coords match {
      case f :: s :: tail => edgesAcc(s :: tail, Edge(f, s) :: acc)
      case _              => acc
    }
}
