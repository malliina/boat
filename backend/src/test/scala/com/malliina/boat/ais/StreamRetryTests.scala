package com.malliina.boat.ais

import cats.effect.{IO, Ref}
import com.malliina.boat.MUnitSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class StreamRetryTests extends MUnitSuite:
  val sleeper = IO.sleep(1.second)
  def starter(since: Long) = IO(println(s"Start ${System.currentTimeMillis() - since}"))
  def printer(since: Long) = IO(println(s"Time ${System.currentTimeMillis() - since}"))
  val failer = IO.raiseError(new Exception("Boom"))

  def unstable(start: Long) = for
    _ <- starter(start)
    _ <- sleeper
    _ <- printer(start)
    _ <- failer
  yield 42

  test("retry failing stream".ignore):
    val start = System.currentTimeMillis()
    fs2.Stream
      .eval(unstable(start))
      .handleErrorWith(t => fs2.Stream.empty)
      .repeat
      .compile
      .drain

  test("retry backoff"):
    val stream =
      for ref <- Ref.of[IO, FiniteDuration](20.millis)
      yield fs2.Stream.eval(ref.getAndUpdate(_ * 2)).repeat.take(2)
    val durations = stream.flatMap(_.compile.toList)
    durations.map: ds =>
      assertEquals(ds, List(20.millis, 40.millis))
