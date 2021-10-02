package com.malliina.boat.ais

import com.malliina.boat.*
import com.malliina.boat.ais.BoatMqttClient.*
import com.nimbusds.jose.util.StandardCharset
import io.circe.parser.parse
import io.circe.{Decoder, DecodingFailure}
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import tests.MUnitSuite

import java.time.Instant

class AISTests extends MUnitSuite:
  val prodFixture = resource(BoatMqttClient.prod(rt))
  val testFixture = resource(BoatMqttClient(TestUrl, MetadataTopic, rt))
  prodFixture.test("MqttSource".ignore) { client =>
    val events = client.slow.take(3).compile.toList.unsafeRunSync()
    events foreach println
  }

  testFixture.test("metadata only".ignore) { client =>
    val messages = client.vesselMessages.take(4).compile.toList.unsafeRunSync()
    messages foreach println
  }

  test("Connect".ignore) {
    val p = new MemoryPersistence
    val date = Instant.now().toEpochMilli
    val client = new MqttClient(ProdUrl.url, s"testclient_$date", p)
    client.setCallback(new MqttCallback:
      override def connectionLost(cause: Throwable): Unit =
        println("Connection lost")

      override def messageArrived(topic: String, message: MqttMessage): Unit =
        val string = new String(message.getPayload, StandardCharset.UTF_8)
        val json = parse(string).toOption.get
        val result: Decoder.Result[AISMessage] = topic match
          case Locations()   => json.as[VesselLocation](VesselLocation.readerGeoJson)
          case Metadata()    => json.as[VesselMetadata](VesselMetadata.readerGeoJson)
          case StatusTopic() => json.as[VesselStatus]
          case other => Left(DecodingFailure(s"Unknown topic: '$other'. JSON: '$json'.", Nil))
        if result.isRight then println(result)

      override def deliveryComplete(token: IMqttDeliveryToken): Unit = ()
    )
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
