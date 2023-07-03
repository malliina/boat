package com.malliina.boat

import io.circe.{Codec, Printer}
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import concurrent.duration.DurationInt

class ModelTests extends munit.FunSuite:
  test("coord cheap hash") {
    val c = Coord(Longitude.build(12.1).toOption.get, Latitude.build(13.412456789).toOption.get)
    assertEquals(c.approx, "12.10000,13.41245")
  }

  test("do not serialize None as null") {
    case class MyClass(name: String, age: Option[Int])
    implicit val codec: Codec[MyClass] = deriveCodec[MyClass]
    val printer = Printer.noSpaces.copy(dropNullValues = true)
    val str = MyClass("Santa", None).asJson.printWith(printer)
    assertEquals(str, """{"name":"Santa"}""")
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
    val result = decode[VesselLocation](in)(VesselLocation.readerGeoJson)
    // println(result)
    assert(result.isRight)
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

    val result = decode[VesselMetadata](in)(VesselMetadata.readerGeoJson)
    // println(result)
    assert(result.isRight)
  }

  test("PingEvent JSON") {
    val string = PingEvent(123, 1.seconds).asJson.noSpaces
    assertEquals(string, """{"event":"ping","body":{"sent":123,"age":1}}""")
  }
