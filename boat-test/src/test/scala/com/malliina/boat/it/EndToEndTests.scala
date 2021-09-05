package com.malliina.boat.it

import cats.effect.{IO, Resource}
import com.malliina.boat.CoordsEvent
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.{DeviceAgent, TcpClient}
import com.malliina.boat.it.EndToEndTests.log
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.io.tcp.{Socket, SocketGroup}
import io.circe.Json

import java.net.InetSocketAddress
import scala.concurrent.Promise

object EndToEndTests {
  private val log = AppLogger(getClass)
}

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
      val plotterOutput: Stream[IO, Byte] = Stream.emits(
        sentences.mkString(TcpClient.linefeed).getBytes(TcpClient.charset)
      ) ++ Stream.empty

      val tcpHost = "127.0.0.1"
      val tcpPort = 10104
      val tcpServer: Resource[IO, Stream[IO, Resource[IO, Socket[IO]]]] = for {
        sockets <- SocketGroup[IO](b)
        server <- sockets.serverResource(new InetSocketAddress(tcpHost, tcpPort))
      } yield server._2
      val firstMessage = Promise[Json]()
      val coordsPromise = Promise[CoordsEvent]()
      tcpServer
        .use[IO, Unit] { clients =>
          val serverUrl = s.baseWsUrl.append(reverse.ws.boats.renderString)
          DeviceAgent(BoatConf.anon(tcpHost, tcpPort), serverUrl, b, httpClient.client).use {
            agent =>
              agent.unsafeConnect()
              clients.head.evalMap { client =>
                client.use[IO, Unit] { clientSocket =>
                  clientSocket.writes()(plotterOutput).compile.drain
                }
              }.compile.drain.map { _ =>
                await(firstMessage.future, 5.seconds)
                agent.close()
              }
          }
        }
        .unsafeRunAsyncAndForget()
      openViewerSocket(httpClient, None) { socket =>
        socket.jsonMessages.map { json =>
          firstMessage.trySuccess(json)
          json.as[CoordsEvent].toOption.foreach { ce =>
            coordsPromise.trySuccess(ce)
          }
        }.compile.drain.unsafeRunAsyncAndForget()
        await(firstMessage.future, 5.seconds)
        await(coordsPromise.future, 5.seconds).coords
      }
  }
}
