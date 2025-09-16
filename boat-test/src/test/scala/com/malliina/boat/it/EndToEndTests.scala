package com.malliina.boat.it

import cats.effect.kernel.Deferred
import cats.effect.IO
import com.comcast.ip4s.*
import com.malliina.boat.{CoordsEvent, TestHttp}
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.{DeviceAgent, TCPClient}
import com.malliina.boat.it.EndToEndTests.log
import com.malliina.http.SocketEvent
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.net.Network
import io.circe.Json

import scala.concurrent.duration.FiniteDuration

object EndToEndTests:
  private val log = AppLogger(getClass)

class EndToEndTests extends BoatTests:
  val sentences = Seq(
    "$SDDPT,23.9,0.0,*43",
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$SDMTW,15.2,C*02",
    "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
    "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
    "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
  )

  override def munitIOTimeout: FiniteDuration = 10.seconds

  /**   1. Starts a plotter (TCP server) and boat backend
    *   1. Connects to plotter and backend with agent
    *   1. Plotter emits sentences (above) to agent
    *   1. Agent sends them to boat
    *   1. Client (frontend) opens socket to boat backend
    *   1. Client receives coordinates event from backend based on sentences
    */
  test("plotter to frontend"):
    val httpClient = TestHttp.client
    val s = server()
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput: Stream[IO, Byte] = Stream.emits(
      sentences.mkString(TCPClient.linefeed).getBytes(TCPClient.charset)
    ) ++ Stream.empty

    val tcpHost = host"127.0.0.1"
    val tcpPort = port"10104"

    SignallingRef[IO, Boolean](false).flatMap: complete =>
      Deferred[IO, Boolean].flatMap: webSocketOpened =>
        Deferred[IO, Json].flatMap: (firstMessage: Deferred[IO, Json]) =>
          Deferred[IO, CoordsEvent].flatMap: (coordsPromise: Deferred[IO, CoordsEvent]) =>
            log.info(s"Starting TCP server at $tcpHost:$tcpPort...")
            val server = Network[IO]
              .server(port = Option(tcpPort))
              .take(1)
              .evalMap: client =>
                webSocketOpened.get >>
                  plotterOutput.through(client.writes).compile.drain
            val serverUrl = s.baseWsUrl.append(reverse.ws.boats.renderString)
            val agent: IO[Json] =
              DeviceAgent
                .fromConf[IO](BoatConf.anon(tcpHost, tcpPort), serverUrl, httpClient)
                .use: agent =>
                  log.info(s"Agent initialized at $tcpHost...")
                  agent.connect
                    .evalMap:
                      case e @ SocketEvent.Open(url) => webSocketOpened.complete(true).as(e)
                      case e                         => IO.pure(e)
                    .compile
                    .resource
                    .lastOrError
                    .use: _ =>
                      firstMessage.get
            val viewer = openViewerSocket(httpClient, None): socket =>
              socket.jsonMessages
                .evalTap: json =>
                  log.debug(s"Viewer got JSON\\n$json...")
                  firstMessage.complete(json) >> json
                    .as[CoordsEvent]
                    .toOption
                    .map(ce => coordsPromise.complete(ce))
                    .getOrElse(IO.pure(false))
                .compile
                .drain
            val system =
              Stream
                .never[IO]
                .concurrently(Stream.eval(viewer))
                .concurrently(server)
                .concurrently(Stream.eval(agent))
                .interruptWhen(complete)
            for
              _ <- system.compile.drain.start
              _ <- firstMessage.get
              cs <- coordsPromise.get
              _ <- complete.getAndSet(true)
            yield cs.coords
