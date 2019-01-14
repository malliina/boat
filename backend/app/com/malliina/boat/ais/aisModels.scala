package com.malliina.boat.ais

import akka.util.ByteString
import com.malliina.http.FullUrl
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

case class MqMessage(topic: String, payload: ByteString)

case class MqttSettings(broker: FullUrl,
                        clientId: String,
                        topic: String,
                        user: String,
                        pass: String,
                        persistence: MqttClientPersistence = new MemoryPersistence,
                        version: Int = 4,
                        bufferSize: Int = 100,
                        qos: MqttQoS = MqttQoS.AtMostOnce)

sealed abstract class MqttQoS(val level: Int)

object MqttQoS {

  case object AtMostOnce extends MqttQoS(0)

  case object AtLeastOnce extends MqttQoS(1)

  case object ExactlyOnce extends MqttQoS(2)

}
