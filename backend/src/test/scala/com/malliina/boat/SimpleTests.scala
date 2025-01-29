package com.malliina.boat

import com.malliina.values.ErrorMessage
import com.malliina.values.Literals.err

extension (sc: StringContext)
  def error(args: Any*): ErrorMessage =
    val msg = sc.s(args*)
    ErrorMessage(msg)

class SimpleTests extends munit.FunSuite:
  test("S3 key parse"):
    val ins = Seq("a", "5", "x5X!-_.*'().jpeg", "$.jpeg", "", " ", "a*", "*")
    val results = ins.map(in => S3Key.build(in).isRight)
    assertEquals(results, Seq(true, true, true, false, false, false, true, false))

  test("Error interpolation"):
    val msg = error"Invalid input"
    assertEquals(msg, ErrorMessage("Invalid input"))

  test("Error interpolation with parameters"):
    val name = "Jack"
    val age = 42
    val msg = error"Unauthorized, $name aged $age"
    assertEquals(msg, ErrorMessage("Unauthorized, Jack aged 42"))

  test("Error with macro"):
    val msg = err"Hey"
    assertEquals(msg, ErrorMessage("Hey"))
