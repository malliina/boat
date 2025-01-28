package com.malliina.boat

import com.malliina.boat.LayerType.Line
import com.malliina.json.Parsing.asJs

import scala.scalajs.js
import scala.scalajs.js.JSON

class ParsingTests extends munit.FunSuite:
  test("Null is dropped when encoding layer"):
    val l =
      Layer("eh", Line, StringLayerSource("src"), None, None, None, maxzoom = Option(11d)).asJs
    val str = JSON.stringify(l)
    assert(str.contains("maxzoom"))
    assert(!str.contains("minzoom"))
