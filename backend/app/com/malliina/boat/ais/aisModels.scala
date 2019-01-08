package com.malliina.boat.ais

import akka.util.ByteString
import com.malliina.http.FullUrl
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

final case class MqMessage(topic: String, payload: ByteString)

final case class MqttSettings(broker: FullUrl,
                              clientId: String,
                              topic: String,
                              user: String,
                              pass: String,
                              persistence: MqttClientPersistence = new MemoryPersistence,
                              version: Int = 4,
                              bufferSize: Int = 100)
