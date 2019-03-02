package com.malliina.boat

import org.scalatest.FunSuite

class BoatTests extends FunSuite {
  test("serialize GeoJSON") {
    val geo = LineGeometry("LineString", Seq(Coord.buildOrFail(24, 60)))
    assert(1 === 1)
  }
}
