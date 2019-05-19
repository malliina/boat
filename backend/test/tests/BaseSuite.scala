package tests

import java.nio.file.Paths

import org.scalatest.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BaseSuite extends FunSuite {
  val userHome = Paths.get(sys.props("user.home"))

  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
}
