package com.malliina.boat.client

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

abstract class BasicSuite extends munit.FunSuite {
  implicit val as = ActorSystem()
  implicit val mat = Materializer(as)
  implicit val ec = mat.executionContext

  def await[T](f: Future[T], duration: Duration = 40.seconds): T = Await.result(f, duration)

  override def afterAll(): Unit = {
    await(as.terminate())
    super.afterAll()
  }
}
