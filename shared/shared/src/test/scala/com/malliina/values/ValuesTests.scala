package com.malliina.values

import com.malliina.values.{degrees, err}

class ValuesTests extends munit.FunSuite:
  test("degs") {
    val _: Degrees = 15f.degrees
  }

  test("macros") {
    val a: ErrorMessage = err"Hej"
    println(a)
  }
