package com.malliina.boat.client

import cats.effect.unsafe.implicits.global
import cats.effect.kernel.Resource
import cats.effect.IO
import munit.FunSuite

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class AsyncSuite extends FunSuite:
  val executor = Executors.newCachedThreadPool()
  implicit val textExecutor: ExecutionContext = ExecutionContext.fromExecutor(executor)
//  implicit val timer: Temporal[IO] = IO.(textExecutor)

  def resource[T](res: Resource[IO, T]): FunFixture[T] =
    var finalizer: Option[IO[Unit]] = None
    FunFixture(
      setup = opts =>
        val (t, f) = res.allocated.unsafeRunSync()
        finalizer = Option(f)
        t,
      teardown = t => finalizer.foreach(_.unsafeRunSync())
    )

  override def afterAll(): Unit =
    super.afterAll()
    executor.shutdownNow()
    executor.awaitTermination(3, TimeUnit.SECONDS)

  def await[T](f: Future[T]) = Await.result(f, 20.seconds)

case class TestResource[T](resource: T, close: IO[Unit])
