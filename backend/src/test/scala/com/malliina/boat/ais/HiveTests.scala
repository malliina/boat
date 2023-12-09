package com.malliina.boat.ais

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.{MqttClientIdentifier, MqttQos}
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth

import java.nio.charset.StandardCharsets
import java.util.UUID

class HiveTests extends munit.FunSuite:
  test("Hive"):
    val client = MqttClient
      .builder()
      .identifier(MqttClientIdentifier.of(UUID.randomUUID().toString))
      .webSocketWithDefaultConfig()
      .sslWithDefaultConfig()
      .serverHost("meri.digitraffic.fi")
      .serverPort(443)
      .useMqttVersion3()
      .buildAsync()
    println(s"Connecting...")
    val ack = client
      .connectWith()
      .simpleAuth(
        Mqtt3SimpleAuth
          .builder()
          .username("digitraffic")
          .password("digitrafficPassword".getBytes(StandardCharsets.UTF_8))
          .build()
      )
      .send()
      .get()
//    val ack = client.connect().get()
    println(s"Subscribing...")
    val sub = client
      .subscribeWith()
      .topicFilter("vessels-v2/#")
      .qos(MqttQos.EXACTLY_ONCE)
      .callback(e => println(s"Message from ${e.getTopic}"))
      .send()
      .get()
    Thread.sleep(10000)
    assertEquals(1, 1)
