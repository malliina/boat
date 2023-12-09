package com.malliina.boat.ais

import org.eclipse.paho.client.mqttv3.{IMqttActionListener, IMqttDeliveryToken, IMqttToken, MqttAsyncClient, MqttCallback, MqttConnectOptions, MqttMessage}
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class TempTests extends munit.FunSuite:
  test("Keepalive"):
    val start = System.currentTimeMillis()
    println(s"Start at $start")

    val client = new MqttAsyncClient(
      "wss://meri.digitraffic.fi:443/mqtt",
      "boat-test client 2",
      new MemoryPersistence
    )
    val connectOptions = new MqttConnectOptions
    connectOptions.setUserName("digitraffic")
    connectOptions.setPassword("digitrafficPassword".toCharArray)
    connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1)
    val topic = "vessels-v2/#"
    client.setCallback(new MqttCallback:
      override def messageArrived(topic: String, message: MqttMessage): Unit =
        println(s"Message from $topic")
      override def connectionLost(cause: Throwable): Unit =
        val duration = System.currentTimeMillis() - start
        println(s"Connection lost after $duration ms $cause")
      override def deliveryComplete(token: IMqttDeliveryToken): Unit =
        ()
    )
    val token = client.connect(
      connectOptions,
      (),
      new IMqttActionListener:
        override def onSuccess(asyncActionToken: IMqttToken): Unit =
          val subscribeCallback = new IMqttActionListener:
            override def onSuccess(asyncActionToken: IMqttToken): Unit =
              println(s"Subscribed to $topic.")
            override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
              println(s"Failed to subscribe to $topic.")
          client
            .subscribe(topic, 0)
            .setActionCallback(subscribeCallback)
        override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
          println(s"Failure $exception")
    )
    assertEquals(1, 1)
