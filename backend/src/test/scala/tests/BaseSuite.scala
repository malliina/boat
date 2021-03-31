package tests

import java.nio.file.Paths
import java.util.concurrent.{Executors, TimeUnit}
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.malliina.boat.http4s.Reverse

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class BaseSuite extends munit.FunSuite {
  val userHome = Paths.get(sys.props("user.home"))
  val reverse = Reverse

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
}
