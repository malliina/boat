package tests

import com.malliina.boat.http4s.Reverse

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BaseSuite extends MUnitSuite {
  val reverse = Reverse

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
}
