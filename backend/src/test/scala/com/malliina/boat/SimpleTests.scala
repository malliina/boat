package com.malliina.boat

class SimpleTests extends MUnitSuite:
  test("S3 key parse"):
    val ins = Seq("a", "5", "x5X!-_.*'().jpeg", "$.jpeg", "", " ", "a*", "*")
    val results = ins.map(in => S3Key.build(in).isRight)
    assertEquals(results, Seq(true, true, true, false, false, false, true, false))
