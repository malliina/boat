package com.malliina.boat

import org.scalatest.FunSuite

class ModelTests extends FunSuite {
  test("coord cheap hash") {
    val c = Coord(12.1, 13.412456789)
    assert(c.approx === "12.10000,13.41245")
  }
}
