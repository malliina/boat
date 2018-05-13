package tests

import com.malliina.boat.AppComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._

abstract class TestAppSuite extends AppSuite(new AppComponents(_))

class AppTestsScalaTest extends TestAppSuite {
  test("can make request") {
    val result = route(app, FakeRequest(GET, "/")).get
    assert(status(result) === 200)
  }
}
