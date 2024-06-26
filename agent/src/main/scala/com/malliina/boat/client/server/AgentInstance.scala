package com.malliina.boat.client.server

import cats.syntax.all.{toFlatMapOps, toFunctorOps}
import cats.effect.kernel.Resource
import cats.effect.Async
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.AgentInstance.log
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.util.AppLogger
import okhttp3.OkHttpClient
import fs2.concurrent.{SignallingRef, Topic}
import fs2.Stream
import fs2.io.net.Network

object AgentInstance:
  private val log = AppLogger(getClass)

  def resource[F[_]: Async: Network](
    http: OkHttpClient
  ): Resource[F, AgentInstance[F]] =
    for
      agent <- Resource.eval(io(http))
      _ <- Stream.emit(()).concurrently(agent.connections).compile.resource.lastOrError
      _ <- Resource.eval(
        AgentSettings
          .readConf()
          .fold(
            err => Async[F].delay(log.warn(s"Failed to read conf: '$err'.")).map(_ => false),
            conf => agent.updateIfNecessary(conf)
          )
      )
    yield agent

  def io[F[_]: Async: Network](http: OkHttpClient): F[AgentInstance[F]] =
    for
      topic <- Topic[F, BoatConf]
      interrupter <- SignallingRef[F, Boolean](false)
    yield AgentInstance(http, topic, interrupter)

class AgentInstance[F[_]: Async: Network](
  http: OkHttpClient,
  confs: Topic[F, BoatConf],
  interrupter: SignallingRef[F, Boolean]
):
  val connections: Stream[F, Unit] = confs
    .subscribe(100)
    .flatMap: newConf =>
      log.info(s"Using conf ${newConf.describe}...")
      val newUrl =
        if newConf.device == GpsDevice then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
      val io = DeviceAgent
        .fromConf(newConf, newUrl, http)
        .use: agent =>
          // Interrupts the previous connection when a new conf appears
          val stream =
            if newConf.enabled then
              agent.connect.interruptWhen(confs.subscribe(1).take(1).map(_ => true))
            else Stream.empty
          stream.compile.drain
      Stream.eval(io)
    .interruptWhen(interrupter)

  def updateIfNecessary(newConf: BoatConf): F[Boolean] =
    confs.publish1(newConf).map(t => t.fold(_ => false, _ => true))

  def close: F[Unit] = interrupter.set(true)
