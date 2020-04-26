package com.malliina.boat.client

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import com.malliina.boat.{Constants, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Await

class WebSocketClientTests extends BasicSuite {
  test("connect boat to boat-tracker.com".ignore) {
    val url = FullUrl.ws("localhost:9000", "/ws/devices")
//    val url = FullUrl.wss("api.boat-tracker.com", "/ws/devices")
    val samples = Seq(
      "$GPZDA,150016.000,17,08,2019,,*51",
      "$GPRMC,150016.000,A,6009.1753,N,02453.2470,E,0.00,166.59,170819,,,A*68",
      "$GPGGA,150016.000,6009.1753,N,02453.2470,E,1,11,0.77,-13.9,M,19.6,M,,*79",
      "$GPGSA,A,3,12,01,03,14,31,18,17,32,11,19,23,,1.14,0.77,0.83*0C",
      "$GPGRS,150016.000,1,-24.0,23.5,18.8,28.5,8.71,5.88,21.1,15.8,8.88,15.5,18.7,*40",
      "$GPGST,150016.000,00020,008.0,006.2,145.0,007.4,006.8,00036*62",
      "$GPTXT,01,01,02,ANTSTATUS=OPEN*2B",
      "$GPGSV,4,1,13,22,78,221,14,01,64,183,22,03,58,257,34,14,51,074,35*71"
    ).map(RawSentence.apply)
    val msg = Json.toJson(SentencesMessage(samples))
    val src = Source(List(msg)).concat(Source.maybe[JsValue])
    val token = "todo"
    val client = WebSocketClient(url, List(KeyValue(Constants.BoatTokenHeader, token)), as, mat)
    try {
      val conn =
        client.connectJson(Sink.foreach[JsValue](println), src.mapMaterializedValue(_ => NotUsed))
      await(conn)
    } finally client.close()

  }
}
