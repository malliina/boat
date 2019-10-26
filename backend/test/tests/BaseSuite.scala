package tests

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait BaseSuite extends FunSuiteLike {
  val userHome = Paths.get(sys.props("user.home"))
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
}

trait AsyncSuite extends BaseSuite with BeforeAndAfterAll {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = mat.executionContext

  override protected def afterAll(): Unit = {
    await(as.terminate())
    super.afterAll()
  }
}
