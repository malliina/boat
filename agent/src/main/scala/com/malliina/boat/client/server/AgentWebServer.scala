package com.malliina.boat.client.server

import cats.effect.{Async, ExitCode, IO, IOApp}
import com.comcast.ip4s.{host, port}
import com.malliina.http.io.HttpClientIO
import com.malliina.util.AppLogger
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder

object AgentWebServer extends IOApp:
  private val log = AppLogger(getClass)

  private def serverResource[F[_]: {Async, Network}] =
    log.info("Initializing server...")
    for
      http <- HttpClientIO.resource[F]
      agentManager <- AgentInstance.resource[F](http)
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
