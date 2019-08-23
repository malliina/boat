package com.malliina.boat.client

import com.malliina.boat.BoatToken
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.FullUrl

class DeviceAgentTests extends BasicSuite {
  ignore("receive-send to dot com") {
    val url = FullUrl.ws("localhost:9000", "/ws/boats")
    val agent = DeviceAgent(BoatConf.anon("192.168.0.11", 10110), url)
    try {
      agent.connect()
      Thread.sleep(30000)
    } finally agent.close()
  }

  ignore("listen to GPS device") {
//    val url = FullUrl.wss("api.boat-tracker.com", "/ws/devices")
    val url = FullUrl.ws("localhost:9000", "/ws/devices")
    val conf = BoatConf("10.0.0.4", 2947, GpsDevice, Option(BoatToken("changeme")), enabled = true)
    val agent = DeviceAgent(conf, url)
    try {
      agent.connect()
      Thread.sleep(10000)
    } finally agent.close()
  }
}
