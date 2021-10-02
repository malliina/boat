package com.malliina.boat.ais

import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import cats.effect.{Concurrent, IO}
import com.malliina.boat.ais.MqttStream.{MqttPayload, log}
import com.malliina.util.AppLogger
import fs2.concurrent.{SignallingRef, Topic}
import fs2.Stream
import org.eclipse.paho.client.mqttv3.*

import java.nio.charset.StandardCharsets

object MqttStream:
  private val log = AppLogger(getClass)

  def apply(settings: MqttSettings, rt: IORuntime): Resource[IO, MqttStream] =
    val task = for
      in <- Topic[IO, MqttPayload]
      signal <- SignallingRef[IO, Boolean](false)
    yield new MqttStream(settings, in, signal)(rt)
    Resource.make(task)(_.close)

  case class MqttPayload(topic: String, payload: Array[Byte]):
    lazy val payloadString = new String(payload, StandardCharsets.UTF_8)

class MqttStream(
  settings: MqttSettings,
  in: Topic[IO, MqttPayload],
  signal: SignallingRef[IO, Boolean]
)(implicit rt: IORuntime):
  val broker = settings.broker
  val client = new MqttAsyncClient(
    broker.url,
    settings.clientId,
    settings.persistence
  )
  val events: Stream[IO, MqttPayload] =
    in.subscribe(100).interruptWhen(signal)

  // TODO .attempts() or .retry()
  client.setCallback(new MqttCallback:
    def messageArrived(topic: String, message: MqttMessage): Unit =
      in.publish1(MqttPayload(topic, message.getPayload)).unsafeRunAsync { e =>
        e.fold(err => log.error(s"Failed to publish MQTT message.", err), _ => ())
      }

    def deliveryComplete(token: IMqttDeliveryToken): Unit = ()

    def connectionLost(cause: Throwable): Unit =
      log.info(s"Connection lost to '$broker'.", cause)
      interrupt()
  )
  val connectOptions = new MqttConnectOptions
  connectOptions.setUserName(settings.user)
  connectOptions.setPassword(settings.pass.toCharArray)
  log.info(s"Connecting to '$broker'...")
  client.connect(
    connectOptions,
    (),
    new IMqttActionListener:
      override def onSuccess(asyncActionToken: IMqttToken): Unit =
        log.info(s"Connected to '$broker'.")
        client.subscribe(settings.topic, settings.qos.level)

      override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
        log.warn(s"Connection lost to '$broker'.", exception)
        interrupt()
        ()
  )

  def interrupt(): Unit = close.unsafeRunSync()
  def close: IO[Unit] = signal.getAndSet(true).map(_ => ())
