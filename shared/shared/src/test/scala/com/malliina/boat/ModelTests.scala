package com.malliina.boat

import org.scalatest.FunSuite
import play.api.libs.json.Json

class ModelTests extends FunSuite {
  test("coord cheap hash") {
    val c = Coord(Longitude(12.1), Latitude(13.412456789))
    assert(c.approx === "12.10000,13.41245")
  }

  test("parse vessel location") {
    val in =
      """
        |{
        |  "mmsi" : 266009000,
        |  "type" : "Feature",
        |  "geometry" : {
        |    "type" : "Point",
        |    "coordinates" : [ 25.496011666666668, 60.01762333333333 ]
        |  },
        |  "properties" : {
        |    "sog" : 0.2,
        |    "cog" : 102.0,
        |    "navStat" : 0,
        |    "rot" : 0,
        |    "posAcc" : false,
        |    "raim" : false,
        |    "heading" : 192,
        |    "timestamp" : 8,
        |    "timestampExternal" : 1546959611874
        |  }
        |}
      """.stripMargin
    val result = VesselLocation.readerGeoJson.reads(Json.parse(in))
    //println(result)
    assert(result.isSuccess)
  }

  test("parse vessel metadata") {
    val in =
      """
        |{
        |  "timestamp" : 1546961299665,
        |  "referencePointD" : 8,
        |  "imo" : 9507130,
        |  "eta" : 851200,
        |  "mmsi" : 248971000,
        |  "referencePointA" : 97,
        |  "referencePointB" : 10,
        |  "draught" : 40,
        |  "posType" : 1,
        |  "destination" : "RUVYG",
        |  "shipType" : 70,
        |  "callSign" : "9HA4892",
        |  "referencePointC" : 7,
        |  "name" : "KLARA"
        |}
      """.stripMargin
    val result = VesselMetadata.readerGeoJson.reads(Json.parse(in))
    //println(result)
    assert(result.isSuccess)
  }

  test("PingEvent JSON") {
    val json = Json.toJson(PingEvent(123))
    assert(Json.stringify(json) === """{"event":"ping","body":{"sent":123}}""")
  }
}
