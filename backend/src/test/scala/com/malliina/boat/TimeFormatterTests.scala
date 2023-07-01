package com.malliina.boat

import tests.MUnitSuite

import java.time.{Clock, Instant}

class TimeFormatterTests extends MUnitSuite:
  test("format time") {
    val now = Instant.now(Clock.systemUTC())
    val str = TimeFormatter.fi.formatDateTime(now)
  }
