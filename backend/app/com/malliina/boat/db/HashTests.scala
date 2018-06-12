package com.malliina.boat.db

import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.FunSuite

class HashTests extends FunSuite {
  test("create hash") {
    val hex = DigestUtils.md5Hex("a:b")
  }
}
