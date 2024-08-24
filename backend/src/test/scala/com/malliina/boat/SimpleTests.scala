package com.malliina.boat

import tests.MUnitSuite

class SimpleTests extends MUnitSuite:
  test("format time"):
//    val now = Instant.now(Clock.systemUTC())
//    val str = TimeFormatter.fi.formatDateTime(now)
    assertEquals(1, 1)

  test("S3 key parse"):
    val ins = Seq("a", "5", "x5X!-_.*'().jpeg", "$.jpeg", "", " ", "a*", "*")
    val results = ins.map(in => S3Key.build(in).isRight)
    assertEquals(results, Seq(true, true, true, false, false, false, true, false))
