package com.malliina.boat.ais

import com.malliina.http.FullUrl
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

case class MqttSettings(
  broker: FullUrl,
  clientId: String,
  topic: String,
  user: String,
  pass: String,
  persistence: MqttClientPersistence = new MemoryPersistence,
  version: Int = 4,
  bufferSize: Int = 100,
  qos: MqttQoS = MqttQoS.AtMostOnce
)

enum MqttQoS(val level: Int):
  case AtMostOnce extends MqttQoS(0)
  case AtLeastOnce extends MqttQoS(1)
  case ExactlyOnce extends MqttQoS(2)
