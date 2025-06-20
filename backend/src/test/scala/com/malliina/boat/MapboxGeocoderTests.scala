package com.malliina.boat

import cats.effect.IO
import cats.effect.syntax.all.concurrentParTraverseOps
import com.malliina.boat.RateLimiter

import scala.concurrent.duration.DurationInt

class MapboxGeocoderTests extends munit.CatsEffectSuite:
  test("Rate limiter works"):
    val permits = 3
    RateLimiter
      .default[IO](tasks = permits, window = 1.second)
      .flatMap: limiter =>
        val tasks = (1 to 5).toList.map: int =>
          IO.delay(int)
        tasks.parTraverseN(100): t =>
          limiter.submit(t)
      .map: list =>
        assertEquals(list.count(_.isDefined), permits)
