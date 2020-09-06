package tests

import java.util.concurrent.{Executors, TimeUnit}

import scala.concurrent.ExecutionContext

abstract class AsyncSuite extends BaseSuite {
  val executor = Executors.newCachedThreadPool()
  implicit val dbExecutor = ExecutionContext.fromExecutor(executor)

  override def afterAll(): Unit = {
    super.afterAll()
    executor.shutdownNow()
    executor.awaitTermination(3, TimeUnit.SECONDS)
  }
}
