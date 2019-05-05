package com.malliina.boat.graph

import com.malliina.boat.{Feature, FeatureCollection}
import org.scalatest.FunSuite
import play.api.libs.json.Json

class GeoTests extends FunSuite {
  ignore("nearest point on line") {
    val porkkala = 24.4001 lngLat 59.9814
    val helsinki = 24.9384 lngLat 60.1699
    val point = 24.40015 lngLat 59.98516
    val nearest = Graph.nearestOnLine(porkkala, helsinki, point)
    val coll = FeatureCollection(
      Seq(
        Feature.line(Seq(porkkala, helsinki)),
        Feature.point(point),
        Feature.point(nearest)
      ))
    println(Json.toJson(coll))
  }
}
