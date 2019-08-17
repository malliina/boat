package com.malliina.boat.client

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.FunSuite

abstract class BasicSuite extends FunSuite{
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext
}
