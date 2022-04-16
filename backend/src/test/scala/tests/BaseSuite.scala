package tests

import com.malliina.boat.http4s.Reverse
import com.malliina.http.io.HttpClientIO

abstract class BaseSuite extends MUnitSuite:
  val reverse = Reverse
  val http = ResourceFixture(HttpClientIO.resource)
