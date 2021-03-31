package com.malliina.boat.db

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import org.apache.commons.codec.digest.DigestUtils

class StringTests extends munit.FunSuite {
  test("create hash") {
    val hex = DigestUtils.md5Hex("a:b")
  }

  test("format") {
    val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    timeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC))
  }
}
