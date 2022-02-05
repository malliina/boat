package com.malliina.boat.client.server

import cats.effect.kernel.{Temporal, Resource}
import cats.effect.IO
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import okhttp3.OkHttpClient
import fs2.concurrent.{SignallingRef, Topic}
import fs2.Stream

object AgentInstance:
  def resource(
    conf: Option[BoatConf],
    url: FullUrl,
    http: OkHttpClient
  ): Resource[IO, AgentInstance] =
    for
      agent <- Resource.eval(io(url, http))
      _ <- agent.connections.compile.resource.lastOrError
    yield agent

  def io(url: FullUrl, http: OkHttpClient): IO[AgentInstance] =
    for
      topic <- Topic[IO, BoatConf]
      interrupter <- SignallingRef[IO, Boolean](false)
    yield AgentInstance(url, http, topic, interrupter)

class AgentInstance(
  url: FullUrl,
  http: OkHttpClient,
  confs: Topic[IO, BoatConf],
  interrupter: SignallingRef[IO, Boolean]
):
  val connections: Stream[IO, Unit] = confs
    .subscribe(100)
    .flatMap { newConf =>
      val newUrl =
        if newConf.device == GpsDevice then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
      Stream.eval(DeviceAgent.fromConf(newConf, newUrl, http)).flatMap { agent =>
        // Interrupts the previous connection when a new conf appears
        if newConf.enabled then
          agent.connect.interruptWhen(confs.subscribe(1).take(1).map(_ => true))
        else Stream.empty
      }
    }
    .interruptWhen(interrupter)

  def updateIfNecessary(newConf: BoatConf): IO[Boolean] =
    confs.publish1(newConf).map { t => t.fold(closed => false, _ => true) }

  def close: IO[Unit] = interrupter.set(true)
