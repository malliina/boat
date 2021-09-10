package com.malliina.boat.client.server

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.io.HttpClientIO
import org.http4s.blaze.server.BlazeServerBuilder
import org.slf4j.LoggerFactory

object AgentWebServer extends IOApp:
  private val log = LoggerFactory.getLogger(getClass)

  val conf = AgentSettings.readConf()
  val url = if conf.device == GpsDevice then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
  val httpResource = Resource.make(IO(HttpClientIO()))(http => IO(http.close()))
  val serverResource = for
    blocker <- Blocker[IO]
    http <- httpResource
    agentManager = AgentInstance(conf, url, blocker, http.client, contextShift, timer)
    service = WebServer(agentManager, blocker, contextShift)
    server <- BlazeServerBuilder[IO](executionContext)
      .bindHttp(port = 8080, "0.0.0.0")
      .withHttpApp(service.service)
      .resource
  yield
    log.info(s"Starting HTTP server at ${server.baseUri}...")
    server

  override def run(args: List[String]): IO[ExitCode] =
    serverResource.use(_ => IO.never).as(ExitCode.Success)
