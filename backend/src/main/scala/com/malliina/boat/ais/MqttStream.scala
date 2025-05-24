package com.malliina.boat.ais

import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.malliina.boat.Mmsi
import com.malliina.boat.ais.MqttStream.{MqttPayload, log}
import com.malliina.util.AppLogger
import com.malliina.values.ErrorMessage
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import org.eclipse.paho.client.mqttv3.*

import java.nio.charset.StandardCharsets

object MqttStream:
  private val log = AppLogger(getClass)

  def resource[F[_]: Async](settings: MqttSettings, d: Dispatcher[F]): Resource[F, MqttStream[F]] =
    val F = Sync[F]
    for
      in <- Resource.eval(Topic[F, MqttPayload])
      signal <- Resource.eval(SignallingRef[F, Boolean](false))
      stream <- Resource.make(F.delay(MqttStream(settings, in, signal, d)))(_.close)
      _ <- Resource.eval(stream.start)
    yield stream

  case class MqttPayload(topic: String, payload: Array[Byte]):
    lazy val payloadString = new String(payload, StandardCharsets.UTF_8)
    private val prefix = "vessels-v2/"
    private val suffixes = Seq("/location", "/metadata")
    val mmsi = suffixes
      .find(s => topic.endsWith(s) && topic.startsWith(prefix))
      .map(s => topic.drop(prefix.length).dropRight(s.length))
      .toRight(ErrorMessage(s"No Mmsi in '$topic'."))
      .flatMap(str => Mmsi.parse(str))

private class MqttStream[F[_]: Async](
  settings: MqttSettings,
  in: Topic[F, MqttPayload],
  signal: SignallingRef[F, Boolean],
  d: Dispatcher[F]
):
  val F = Async[F]
  private val broker = settings.broker
  private val client = new MqttAsyncClient(broker.url, settings.clientId, settings.persistence)
  val events: Stream[F, MqttPayload] = in.subscribe(100).interruptWhen(signal)

  private val connectOptions = new MqttConnectOptions
  connectOptions.setUserName(settings.user)
  connectOptions.setPassword(settings.pass.toCharArray)

  private val connect: F[IMqttToken] = F
    .async_[IMqttToken]: cb =>
      log.info(s"Connecting to '$broker'...")
      client.connect(
        connectOptions,
        (),
        new IMqttActionListener:
          override def onSuccess(asyncActionToken: IMqttToken): Unit =
            log.info(s"Connected to '$broker'. Subscribing to '${settings.topic}'...")
            client.subscribe(settings.topic, settings.qos.level)
            cb(Right(asyncActionToken))

          override def onFailure(asyncActionToken: IMqttToken, exception: Throwable): Unit =
            log.warn(s"Connection lost to '$broker'.", exception)
            cb(Left(exception))
      )
    .onError(_ => close)
  // Converts a callback-based Unit-typed API to IO, making events available in topic `in`.
  val start = for
    _ <- F.delay:
      client.setCallback(new MqttCallback:
        def messageArrived(topic: String, message: MqttMessage): Unit =
          val payload = MqttPayload(topic, message.getPayload)
          val task = in
            .publish1(payload)
            .map: e =>
              e.fold(err => log.error(s"Failed to publish MQTT message.", err), _ => ())
          d.unsafeRunAndForget(task)
        def deliveryComplete(token: IMqttDeliveryToken): Unit = ()
        def connectionLost(cause: Throwable): Unit =
          val task = F.delay(log.info(s"Connection lost to '$broker'.", cause)) >> close
          d.unsafeRunAndForget(task)
      )
    connection <- connect
  yield connection

  val close: F[Unit] = signal.getAndSet(true).map(_ => ())
