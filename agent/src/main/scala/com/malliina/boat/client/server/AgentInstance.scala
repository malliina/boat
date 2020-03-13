package com.malliina.boat.client.server

import akka.actor.ActorSystem
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl

object AgentInstance {
  def apply(initial: BoatConf, url: FullUrl, as: ActorSystem): AgentInstance =
    new AgentInstance(initial, url)(as)
}

class AgentInstance(initialConf: BoatConf, url: FullUrl)(implicit as: ActorSystem) {
  private var conf = initialConf
  private var agent = DeviceAgent(conf, url)
  if (initialConf.enabled) {
    agent.connect()
  }

  def updateIfNecessary(newConf: BoatConf): Boolean = synchronized {
    if (newConf != conf) {
      val newUrl =
        if (newConf.device == GpsDevice) DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
      conf = newConf
      val oldAgent = agent
      oldAgent.close()
      val newAgent = DeviceAgent(newConf, newUrl)
      agent = newAgent
      if (newConf.enabled) {
        newAgent.connect()
      }
      true
    } else {
      false
    }
  }
}
