package tests

import cats.effect.IO

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.ExecutionContext

abstract class AsyncSuite extends BaseSuite:
  val executor = Executors.newCachedThreadPool()
  implicit val textExecutor: ExecutionContext = ExecutionContext.fromExecutor(executor)

  override def afterAll(): Unit =
    super.afterAll()
    executor.shutdownNow()
    executor.awaitTermination(3, TimeUnit.SECONDS)
