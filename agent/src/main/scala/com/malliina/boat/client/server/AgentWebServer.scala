package com.malliina.boat.client.server

import cats.effect.{Async, ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host, port}
import com.malliina.boat.client.DeviceAgent
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import org.http4s.ember.server.EmberServerBuilder
import org.slf4j.LoggerFactory

object AgentWebServer extends IOApp:
  private val log = AppLogger(getClass)

  val conf = AgentSettings.readConf().toOption
  val url =
    if conf.exists(_.device == GpsDevice) then DeviceAgent.DeviceUrl else DeviceAgent.BoatUrl
  def serverResource[F[_]: Async] = for
    http <- HttpClientIO.resource[F]
    agentManager <- AgentInstance.resource[F](url, http.client)
    service = WebServer(agentManager)
    server <- EmberServerBuilder
      .default[F]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(service.service)
      .build
  yield
    log.info(s"Starting HTTP server at ${server.baseUri}...")
    server

  override def run(args: List[String]): IO[ExitCode] =
    serverResource[IO].use(_ => IO.never).as(ExitCode.Success)
