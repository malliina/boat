package com.malliina.boat

import org.scalatest.FunSuite

class BoatTests extends FunSuite {
  test("serialize GeoJSON") {
    val geo = Geometry("LineString", Seq(Coord(24, 60)))
    assert(1 === 1)
  }
}
