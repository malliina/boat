package com.malliina.boat.client.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice

object AgentWebServer {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("agent-system")
    implicit val materializer = ActorMaterializer()

    val conf = AgentSettings.readConf()
    val url = if (conf.device == GpsDevice) DeviceAgent.ProdDeviceUrl else DeviceAgent.ProdBoatUrl
    val agentManager = AgentInstance(conf, url, system, materializer)
    WebServer("0.0.0.0", 8080, agentManager)
  }
}
