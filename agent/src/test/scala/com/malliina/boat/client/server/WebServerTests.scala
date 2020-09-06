package com.malliina.boat.client.server

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import com.malliina.boat.BoatToken
import com.malliina.boat.client.{BasicSuite, DeviceAgent}

class WebServerTests extends BasicSuite {
  test("json") {
    object json extends JsonSupport

    assert(json.tokenFormat.write(BoatToken("demo")).compactPrint == "\"demo\"")
  }

  test("get") {
    val server =
      WebServer("127.0.0.1", 0, AgentInstance(BoatConf.empty, DeviceAgent.BoatUrl, as))
    val testServer = Http().newServerAt("127.0.0.1", 0).bindFlow(server.routes)
    val binding = await(testServer)
    val addr = binding.localAddress
    def urlTo(path: String) = s"http://${addr.getHostString}:${addr.getPort}$path"

    val res = await(
      Http()
        .singleRequest(HttpRequest(method = HttpMethods.GET, uri = urlTo(WebServer.settingsUri)))
    )
    assert(res.status == StatusCodes.OK)
  }

  test("initial pass hash") {
    assert(WebServer.hash("boat") == WebServer.defaultHash)
  }
}
