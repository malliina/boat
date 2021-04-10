package tests

import akka.actor.ActorSystem
import akka.stream.Materializer

class AkkaStreamsSuite extends AsyncSuite {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat = Materializer(as)

  override def afterAll(): Unit = {
    await(as.terminate())
    super.afterAll()
  }
}
