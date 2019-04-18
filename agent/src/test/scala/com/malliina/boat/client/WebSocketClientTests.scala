package com.malliina.boat.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.malliina.boat.Constants
import org.scalatest.FunSuite
import play.api.libs.json.JsValue

import scala.concurrent.Await

class WebSocketClientTests extends FunSuite {
  ignore("connect boat to boat-tracker.com") {
    implicit val as = ActorSystem()
    implicit val mat = ActorMaterializer()
    val token = "todo"
    val client = WebSocketClient(List(KeyValue(Constants.BoatTokenHeader, token)), as, mat)
    val conn = client.connectJson(Sink.foreach[JsValue](println),
                                  Source.maybe[JsValue].mapMaterializedValue(_ => NotUsed))
    Await.result(conn, 20.seconds)
  }
}
