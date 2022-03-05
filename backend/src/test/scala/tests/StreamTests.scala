package tests

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import fs2.Stream
import concurrent.duration.DurationInt

class StreamTests extends munit.FunSuite:
  test("stream concurrently".ignore) {
    val secs = fs2.Stream.awakeEvery[IO](1.seconds).map(d => s"One: $d").take(3)
    val secs2 = fs2.Stream.awakeEvery[IO](3.seconds).map(d => s"Three: $d").take(3)
    secs.merge(secs2).map(println).compile.drain.unsafeRunSync()
  }
