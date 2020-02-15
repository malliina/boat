package com.malliina.boat.client.server

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice

object AgentWebServer {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("agent-system")
    val conf = AgentSettings.readConf()
    val url = if (conf.device == GpsDevice) DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
    val agentManager = AgentInstance(conf, url, system)
    WebServer("0.0.0.0", 8080, agentManager)
  }
}
