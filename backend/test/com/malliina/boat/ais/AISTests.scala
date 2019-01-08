package com.malliina.boat.ais

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.malliina.boat._
import com.malliina.http.FullUrl
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import play.api.libs.json.{JsError, Json}
import tests.BaseSuite

import scala.concurrent.duration.DurationInt

class AISTests extends BaseSuite {
  //val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")

  test("MqttSource") {
    val client = MqClient()
    val as = ActorSystem("test")
    implicit val mat = ActorMaterializer()(as)
    val fut = client.slow.take(3).runWith(Sink.foreach(msg => println(msg)))
    await(fut, 100.seconds)
  }

  ignore("Connect") {
    val p = new MemoryPersistence
    val date = Instant.now().toEpochMilli
    val client = new MqttClient(ProdUrl.url, s"testclient_$date", p)
    client.setCallback(new MqttCallback {
      override def connectionLost(cause: Throwable): Unit =
        println("Connection lost")

      override def messageArrived(topic: String, message: MqttMessage): Unit = {
        val json = Json.parse(message.getPayload)
        val result = topic match {
          case Locations() => VesselLocation.readerGeoJson.reads(json)
          case Metadata() => VesselMetadata.json.reads(json)
          case Status() => VesselStatus.reader.reads(json)
          case other => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
        }
        if (result.isError)
          println(result)
      }

      override def deliveryComplete(token: IMqttDeliveryToken): Unit = ()
    })
    val opts = new MqttConnectOptions
    opts.setCleanSession(true)
    opts.setUserName("digitraffic")
    opts.setPassword("digitrafficPassword".toCharArray)
    opts.setMqttVersion(4)
    client.connect(opts)
    println("Connected")
    client.subscribe("vessels/#")
    println("Subscribed")
    Thread.sleep(10000)
  }
}
