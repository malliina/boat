package com.malliina.boat

class UtilsTests extends munit.FunSuite {
  test("Utils.normalize") {
    val in = "a b å ÄÖ@!%<>10.9.2018"
    assert(Utils.normalize(in) == "a-b-a-ao-----10-9-2018")
  }
}
