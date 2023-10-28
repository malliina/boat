package com.malliina.boat

import com.malliina.values.{lng, lat}
import io.circe.{Codec, Printer}
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.{decode, parse}
import io.circe.syntax.EncoderOps
import concurrent.duration.DurationInt

class ModelTests extends munit.FunSuite:
  test("coord cheap hash"):
    val c = Coord(12.1.lng, 13.412456789.lat)
    assertEquals(c.approx, "12.10000,13.41245")

  test("do not serialize None as null"):
    case class MyClass(name: String, age: Option[Int])
    implicit val codec: Codec[MyClass] = deriveCodec[MyClass]
    val printer = Printer.noSpaces.copy(dropNullValues = true)
    val str = MyClass("Santa", None).asJson.printWith(printer)
    assertEquals(str, """{"name":"Santa"}""")

  test("parse vessel location"):
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

  test("parse vessel metadata"):
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

  test("PingEvent JSON"):
    val string = PingEvent(123, 1.seconds).asJson
    val expected = parse("""{"event":"ping","body":{"sent":123,"age":1.0}}""").toOption.get
    assertEquals(string, expected)

  test("Events json"):
    val inp =
      """
        |{
        |  "event": "coords",
        |  "body": {
        |    "coords": [
        |      {
        |        "depth": 0,
        |        "id": 1507274,
        |        "coord": {
        |          "lng": 24.88320285,
        |          "lat": 60.15414054
        |        },
        |        "boatTime": "27.10.2023 19:27:13",
        |        "boatTimeMillis": 1698424033000,
        |        "boatTimeOnly": "19:27:13",
        |        "speed": 0.0,
        |        "altitude": 46.000732421875,
        |        "outsideTemp": 3.0,
        |        "waterTemp": 0.0,
        |        "depthMeters": 0.0,
        |        "time": {
        |          "date": "27.10.2023",
        |          "time": "19:27:13",
        |          "dateTime": "27.10.2023 19:27:13",
        |          "millis": 1698424033000
        |        }
        |      }
        |    ],
        |    "from": {
        |      "distance": 740647,
        |      "track": 2794,
        |      "trackName": "slyyxy",
        |      "trackTitle": null,
        |      "canonical": "slyyxy",
        |      "comments": null,
        |      "boat": 1319,
        |      "boatName": "Mos",
        |      "sourceType": "vehicle",
        |      "username": "mle",
        |      "points": 187,
        |      "duration": 220.0,
        |      "distanceMeters": 740.6478150355647,
        |      "topSpeed": 23.28772354211663,
        |      "avgSpeed": 8.39851862850972,
        |      "avgWaterTemp": null,
        |      "avgOutsideTemp": 2.304812834224599,
        |      "topPoint": {
        |        "depth": 0,
        |        "id": 1507348,
        |        "coord": {
        |          "lng": 24.88176911,
        |          "lat": 60.1532371
        |        },
        |        "boatTime": "27.10.2023 19:29:00",
        |        "boatTimeMillis": 1698424140000,
        |        "boatTimeOnly": "19:29:00",
        |        "speed": 23.28772354211663,
        |        "altitude": 36.74737548828125,
        |        "outsideTemp": 2.0,
        |        "waterTemp": 0.0,
        |        "depthMeters": 0.0,
        |        "time": {
        |          "date": "27.10.2023",
        |          "time": "19:29:00",
        |          "dateTime": "27.10.2023 19:29:00",
        |          "millis": 1698424140000
        |        }
        |      },
        |      "times": {
        |        "start": {
        |          "date": "27.10.2023",
        |          "time": "19:27:13",
        |          "dateTime": "27.10.2023 19:27:13",
        |          "millis": 1698424033000
        |        },
        |        "end": {
        |          "date": "27.10.2023",
        |          "time": "19:30:53",
        |          "dateTime": "27.10.2023 19:30:53",
        |          "millis": 1698424253000
        |        },
        |        "range": "27.10.2023 19:27:13 - 19:30:53"
        |      }
        |    }
        |  }
        |}
        |""".stripMargin
    val result = decode[CoordsEvent](inp)
    assert(result.isRight)
