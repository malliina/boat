package com.malliina.boat.ais

import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.{Concurrent, IO, Async}
import com.malliina.boat.ais.MqttStream.{MqttPayload, log}
import com.malliina.util.AppLogger
import fs2.concurrent.{SignallingRef, Topic}
import fs2.Stream
import org.eclipse.paho.client.mqttv3.*

import java.nio.charset.StandardCharsets

object MqttStream:
  private val log = AppLogger(getClass)

  def resource(settings: MqttSettings, d: Dispatcher[IO]): Resource[IO, MqttStream] =
    for
      in <- Resource.eval(Topic[IO, MqttPayload])
      signal <- Resource.eval(SignallingRef[IO, Boolean](false))
      stream <- Resource.make(IO(MqttStream(settings, in, signal, d)))(_.close)
      _ <- Resource.eval(stream.start)
    yield stream

  case class MqttPayload(topic: String, payload: Array[Byte]):
    lazy val payloadString = new String(payload, StandardCharsets.UTF_8)

private class MqttStream(
  settings: MqttSettings,
  in: Topic[IO, MqttPayload],
  signal: SignallingRef[IO, Boolean],
  d: Dispatcher[IO]
):
  private val broker = settings.broker
  private val client = new MqttAsyncClient(broker.url, settings.clientId, settings.persistence)
  val events: Stream[IO, MqttPayload] = in.subscribe(100).interruptWhen(signal)

  private val connectOptions = new MqttConnectOptions
  connectOptions.setUserName(settings.user)
  connectOptions.setPassword(settings.pass.toCharArray)

  private val connect: IO[IMqttToken] = IO
    .async_[IMqttToken] { cb =>
      log.info(s"Connecting to '$broker'...")
      client.connect(
        connectOptions,
        (),
        new IMqttActionListener:
          override def onSuccess(asyncActionToken: IMqttToken): Unit =
            log.info(s"Connected to '$broker'.")
            client.subscribe(settings.topic, settings.qos.level)
            cb(Right(asyncActionToken))

          override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
            log.warn(s"Connection lost to '$broker'.", exception)
            cb(Left(exception))
      )
    }
    .onError(t => close)

  // Converts a callback-based Unit-typed API to IO, making events available in topic `in`.
  val start = for
    installCallback <- IO.delay {
      client.setCallback(new MqttCallback:
        def messageArrived(topic: String, message: MqttMessage): Unit =
          val payload = MqttPayload(topic, message.getPayload)
          val task = in.publish1(payload).map { e =>
            e.fold(err => log.error(s"Failed to publish MQTT message.", err), _ => ())
          }
          d.unsafeRunAndForget(task)
        def deliveryComplete(token: IMqttDeliveryToken): Unit = ()
        def connectionLost(cause: Throwable): Unit =
          val task = IO(log.info(s"Connection lost to '$broker'.", cause)) >> close
          d.unsafeRunAndForget(task)
      )
    }
    connection <- connect
  yield connection

  val close: IO[Unit] = signal.getAndSet(true).map(_ => ())
