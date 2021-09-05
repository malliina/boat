package com.malliina.boat.client.server

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl
import okhttp3.OkHttpClient

object AgentInstance {
  def apply(
    initial: BoatConf,
    url: FullUrl,
    blocker: Blocker,
    http: OkHttpClient,
    cs: ContextShift[IO],
    t: Timer[IO]
  ): AgentInstance = {
    new AgentInstance(initial, url, blocker, http)(cs, t)
  }
}

class AgentInstance(initialConf: BoatConf, url: FullUrl, blocker: Blocker, http: OkHttpClient)(
  implicit
  cs: ContextShift[IO],
  t: Timer[IO]
) {
  private var conf = initialConf
  private var (agent, finalizer) = DeviceAgent(conf, url, blocker, http).allocated.unsafeRunSync()
  if (initialConf.enabled) {
    agent.connect().unsafeRunAsyncAndForget()
  }

  def updateIfNecessary(newConf: BoatConf): Boolean = synchronized {
    if (newConf != conf) {
      val newUrl =
        if (newConf.device == GpsDevice) DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
      conf = newConf
      val oldAgent = agent
      finalizer.unsafeRunSync()
      oldAgent.close()
      val newAgentResource = DeviceAgent(newConf, newUrl, blocker, http)
      val (newAgent, newFinalizer) = newAgentResource.allocated.unsafeRunSync()
      agent = newAgent
      finalizer = newFinalizer
      if (newConf.enabled) {
        newAgent.connect().unsafeRunAsyncAndForget()
      }
      true
    } else {
      false
    }
  }
}
