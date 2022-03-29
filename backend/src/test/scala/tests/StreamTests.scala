package tests

import cats.effect.IO
import cats.effect.kernel.Deferred
import fs2.Stream

import scala.concurrent.duration.DurationInt

class StreamTests extends munit.CatsEffectSuite:
  test("stream concurrently".ignore) {
    val secs = fs2.Stream.awakeEvery[IO](1.seconds).map(d => s"One: $d").take(3)
    val secs2 = fs2.Stream.awakeEvery[IO](3.seconds).map(d => s"Three: $d").take(3)
    secs.merge(secs2).map(println).compile.drain
  }

  test("defer") {
    val dv = for
      d <- Deferred[IO, Int]
      b <- d.complete(42)
      v <- d.get
    yield v
    assertIO(dv, 42)
  }
