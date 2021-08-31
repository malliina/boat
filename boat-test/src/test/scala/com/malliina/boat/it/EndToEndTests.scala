package com.malliina.boat.it

import cats.effect.IO
import com.malliina.boat.CoordsEvent
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.{DeviceAgent, TcpClient}
import fs2.io.tcp.SocketGroup
import fs2.{Chunk, Stream}
import io.circe.Json

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.Promise

class EndToEndTests extends BoatTests {
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

  val sockets = resource(blocker.flatMap { b => SocketGroup[IO](b) })

  FunFixture.map2(http, resource(blocker)).test("plotter to frontend") {
    case (httpClient, b) =>
      val s = server()
      // the client validates maximum frame length, so we must not concatenate multiple sentences

      val plotterOutput: Stream[IO, Array[Byte]] = Stream.emits(
        sentences.map(s => s"$s${TcpClient.crlf}".getBytes(StandardCharsets.US_ASCII)).toList
      ) ++ Stream.never

      val tcpHost = "127.0.0.1"
      val tcpPort = 10104
      val tcpServer = for {
        sockets <- SocketGroup[IO](b)
        server <- sockets.serverResource(new InetSocketAddress(tcpHost, tcpPort))
      } yield server._2
      tcpServer
        .use[IO, Unit] { clients =>
          val serverUrl = s.baseWsUrl.append(reverse.ws.boats.renderString)
          DeviceAgent(BoatConf.anon(tcpHost, tcpPort), serverUrl, b, httpClient.client).use {
            agent =>
              try {
                val firstMessage = Promise[Json]()
                val coordsPromise = Promise[CoordsEvent]()
                openViewerSocket(httpClient, None) { socket =>
                  socket.jsonMessages.map { json =>
                    firstMessage.trySuccess(json)
                    json.as[CoordsEvent].toOption.foreach { ce =>
                      coordsPromise.trySuccess(ce)
                    }
                  }.compile.drain.unsafeRunAsyncAndForget()
                  await(firstMessage.future, 10.seconds)
                  await(coordsPromise.future).coords
                }
              } finally {
                agent.close()
              }
              clients.evalMap { client =>
                client.use[IO, Unit] { clientSocket =>
                  val byteStream =
                    plotterOutput.map(Chunk.bytes).flatMap(c => Stream.emits(c.toList))
                  clientSocket.writes()(byteStream).compile.drain
                }
              }.compile.drain
          }
        }
        .unsafeRunSync()
  }
}
