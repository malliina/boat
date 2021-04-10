package com.malliina.boat.ais

import cats.effect.{Concurrent, IO}
import com.malliina.boat.ais.MqttStream.{MqttPayload, log}
import com.malliina.util.AppLogger
import fs2.concurrent.{SignallingRef, Topic}
import org.eclipse.paho.client.mqttv3._

import java.nio.charset.StandardCharsets

object MqttStream {
  private val log = AppLogger(getClass)

  def unsafe(settings: MqttSettings, interrupter: SignallingRef[IO, Boolean])(implicit
    c: Concurrent[IO]
  ): MqttStream = apply(
    settings,
    Topic[IO, MqttPayload](MqttPayload(settings.topic, Array.empty[Byte])).unsafeRunSync(),
    interrupter,
    c
  )

  def apply(
    settings: MqttSettings,
    in: Topic[IO, MqttPayload],
    signal: SignallingRef[IO, Boolean],
    c: Concurrent[IO]
  ): MqttStream =
    new MqttStream(settings, in, signal)(c)

  case class MqttPayload(topic: String, payload: Array[Byte]) {
    lazy val payloadString = new String(payload, StandardCharsets.UTF_8)
  }
}

class MqttStream(
  settings: MqttSettings,
  in: Topic[IO, MqttPayload],
  signal: SignallingRef[IO, Boolean]
)(implicit c: Concurrent[IO]) {
  val broker = settings.broker
  val client = new MqttAsyncClient(
    broker.url,
    settings.clientId,
    settings.persistence
  )
  val events: fs2.Stream[IO, MqttPayload] =
    in.subscribe(100).drop(1).interruptWhen(signal)
  // TODO .attempts() or .retry()
  client.setCallback(new MqttCallback {
    def messageArrived(topic: String, message: MqttMessage): Unit = {
      in.publish1(MqttPayload(topic, message.getPayload)).unsafeRunAsync { e =>
        e.fold(err => log.error(s"Failed to publish MQTT message.", err), _ => ())
      }
    }

    def deliveryComplete(token: IMqttDeliveryToken): Unit = ()

    def connectionLost(cause: Throwable): Unit = {
      log.info(s"Connection lost to '$broker'.", cause)
      interrupt()
    }
  })
  val connectOptions = new MqttConnectOptions
  connectOptions.setUserName(settings.user)
  connectOptions.setPassword(settings.pass.toCharArray)
  log.info(s"Connecting to '$broker'...")
  client.connect(
    connectOptions,
    (),
    new IMqttActionListener {
      override def onSuccess(asyncActionToken: IMqttToken): Unit = {
        log.info(s"Connected to '$broker'.")
        client.subscribe(settings.topic, settings.qos.level)
      }

      override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit = {
        log.warn(s"Connection lost to '$broker'.", exception)
        interrupt()
        ()
      }
    }
  )

  def interrupt(): Unit = signal.set(true).unsafeRunSync()
  def close(): Unit = interrupt()
}
