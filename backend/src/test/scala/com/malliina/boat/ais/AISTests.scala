package com.malliina.boat.ais

import com.malliina.boat._
import com.malliina.boat.ais.BoatMqttClient._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import play.api.libs.json.{JsError, Json}
import tests.MUnitSuite

import java.time.Instant

class AISTests extends MUnitSuite {
  test("MqttSource".ignore) {
    val client = BoatMqttClient.prod()
    val events = client.slow.take(3).compile.toList.unsafeRunSync()
    events foreach println
  }

  test("metadata only".ignore) {
    val client = BoatMqttClient(TestUrl, MetadataTopic)
    val messages = client.vesselMessages.take(4).compile.toList.unsafeRunSync()
    messages foreach println
  }

  test("Connect".ignore) {
    val p = new MemoryPersistence
    val date = Instant.now().toEpochMilli
    val client = new MqttClient(ProdUrl.url, s"testclient_$date", p)
    client.setCallback(new MqttCallback {
      override def connectionLost(cause: Throwable): Unit =
        println("Connection lost")

      override def messageArrived(topic: String, message: MqttMessage): Unit = {
        val json = Json.parse(message.getPayload)
        val result = topic match {
          case Locations()   => VesselLocation.readerGeoJson.reads(json)
          case Metadata()    => VesselMetadata.readerGeoJson.reads(json)
          case StatusTopic() => VesselStatus.reader.reads(json)
          case other         => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
        }
        if (result.isError)
          println(result)
      }

      override def deliveryComplete(token: IMqttDeliveryToken): Unit = ()
    })
    val opts = new MqttConnectOptions
    opts.setCleanSession(true)
    opts.setUserName(user)
    opts.setPassword(pass.toCharArray)
    opts.setMqttVersion(4)
    client.connect(opts)
    println("Connected")
    client.subscribe("vessels/#")
    println("Subscribed")
    Thread.sleep(10000)
  }
}