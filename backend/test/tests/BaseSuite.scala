package tests

import org.scalatest.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BaseSuite extends FunSuite {
  val reverse = controllers.routes.BoatController

  def await[T](f: Future[T], duration: Duration = 10.seconds): T = Await.result(f, duration)
}