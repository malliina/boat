package com.malliina.boat.client.server

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import org.http4s.blaze.server.BlazeServerBuilder
import org.slf4j.LoggerFactory

object AgentWebServer extends IOApp:
  private val log = AppLogger(getClass)

  val conf = AgentSettings.readConf().toOption
  val url =
    if conf.exists(_.device == GpsDevice) then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
  val httpResource = Resource.make(IO(HttpClientIO()))(http => IO(http.close()))
  val serverResource = for
    http <- httpResource
    agentManager <- AgentInstance.resource(conf, url, http.client)
    service = WebServer(agentManager)
    server <- BlazeServerBuilder[IO]
      .bindHttp(port = 8080, "0.0.0.0")
      .withHttpApp(service.service)
      .withBanner(Nil)
      .resource
  yield
    log.info(s"Starting HTTP server at ${server.baseUri}...")
    server

  override def run(args: List[String]): IO[ExitCode] =
    serverResource.use(_ => IO.never).as(ExitCode.Success)
