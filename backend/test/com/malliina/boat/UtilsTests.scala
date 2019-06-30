package com.malliina.boat

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime}

import com.malliina.boat.db.BoatSchema
import org.scalatest.FunSuite

class UtilsTests extends FunSuite {
  test("Utils.normalize") {
    val in = "a b å ÄÖ@!%<>10.9.2018"
    assert(Utils.normalize(in) === "a-b-a-ao-----10-9-2018")
  }
}
