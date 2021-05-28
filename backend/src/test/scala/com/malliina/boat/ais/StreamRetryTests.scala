package com.malliina.boat.ais

import cats.effect.{IO, Timer}
import munit.FunSuite

import scala.concurrent.duration.DurationInt

class StreamRetryTests extends FunSuite {
  implicit def munitTimer: Timer[IO] =
    IO.timer(munitExecutionContext)

  val sleeper = IO.sleep(1.second)
  def starter(since: Long) = IO(println(s"Start ${System.currentTimeMillis() - since}"))
  def printer(since: Long) = IO(println(s"Time ${System.currentTimeMillis() - since}"))
  val failer = IO.raiseError(new Exception("Boom"))

  def unstable(start: Long) = for {
    _ <- starter(start)
    _ <- sleeper
    _ <- printer(start)
    _ <- failer
  } yield 42

  test("retry failing stream".ignore) {
    val start = System.currentTimeMillis()
    val hm = fs2.Stream
      .eval(unstable(start))
      .handleErrorWith(t => fs2.Stream.empty)
      .repeat
      .compile
      .drain
      .unsafeRunSync()
  }
}
