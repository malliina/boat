package tests

import com.malliina.boat.http4s.Reverse
import com.malliina.http.io.HttpClientIO

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

abstract class BaseSuite extends MUnitSuite:
  val reverse = Reverse

  val http = FunFixture[HttpClientIO](
    opts => HttpClientIO(),
    teardown = _.close()
  )

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)
