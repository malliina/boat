package com.malliina.boat

import com.malliina.json.Parsing.asJs
import com.malliina.mapbox.LayerType.Line
import com.malliina.mapbox.{Layer, StringLayerSource}

import scala.scalajs.js.JSON

class ParsingTests extends munit.FunSuite:
  test("Null is dropped when encoding layer"):
    val l =
      Layer(
        "eh",
        Line,
        StringLayerSource.unsafe("src"),
        None,
        None,
        None,
        maxzoom = Option(11d)
      ).asJs
    val str = JSON.stringify(l)
    assert(str.contains("maxzoom"))
    assert(!str.contains("minzoom"))
