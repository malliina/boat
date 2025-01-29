package com.malliina.values

import com.malliina.values.{degrees, error}

class ValuesTests extends munit.FunSuite:
  test("degs"):
    val _: Degrees = 15f.degrees

  test("macros"):
    val x = 42
    val a: ErrorMessage = s"Hej, $x".error
    assertEquals(a, ErrorMessage("Hej, 42"))
