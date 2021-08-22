package com.malliina.boat.it

import akka.actor.ActorSystem
import akka.stream.Materializer
import tests.AsyncSuite

class AkkaStreamsSuite extends AsyncSuite {
  implicit val as: ActorSystem = ActorSystem()
  implicit val mat = Materializer(as)

  override def afterAll(): Unit = {
    await(as.terminate())
    super.afterAll()
  }
}
