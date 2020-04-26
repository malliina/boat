package tests

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class BaseSuite extends munit.FunSuite {
  val userHome = Paths.get(sys.props("user.home"))
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
}

class AsyncSuite extends BaseSuite {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat = Materializer(as)
  implicit val ec: ExecutionContext = mat.executionContext

  override def afterAll(): Unit = {
    await(as.terminate())
    super.afterAll()
  }
}
