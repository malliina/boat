package com.malliina.boat.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.malliina.boat.{Constants, SentencesMessage}
import com.malliina.http.FullUrl
import org.scalatest.FunSuite
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Await

class WebSocketClientTests extends FunSuite {
  ignore("connect boat to boat-tracker.com") {
//    val url = FullUrl.ws("localhost:9000", "/ws/boats")
    val url = FullUrl.wss("www.boat-tracker.com", "/ws/boats")

    implicit val as = ActorSystem()
    implicit val mat = ActorMaterializer()
    val msg = Json.toJson(SentencesMessage(Nil))
    val src = Source(List(msg, msg, msg, msg, msg)).concat(Source.maybe[JsValue])
    val token = "todo"
    //    val client = WebSocketClient(url, Nil, as, mat)
    val client = WebSocketClient(List(KeyValue(Constants.BoatTokenHeader, token)), as, mat)
    try {
      val conn = client.connectJson(Sink.foreach[JsValue](println),
        src.mapMaterializedValue(_ => NotUsed))
      Await.result(conn, 20.seconds)
    } finally client.close()

  }
}
