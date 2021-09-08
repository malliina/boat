package tests

import cats.effect.{ContextShift, IO, Timer}

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.ExecutionContext

abstract class AsyncSuite extends BaseSuite {
  val executor = Executors.newCachedThreadPool()
  implicit val textExecutor: ExecutionContext = ExecutionContext.fromExecutor(executor)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(textExecutor)
  implicit val timer: Timer[IO] = IO.timer(textExecutor)

  override def afterAll(): Unit = {
    super.afterAll()
    executor.shutdownNow()
    executor.awaitTermination(3, TimeUnit.SECONDS)
  }
}
