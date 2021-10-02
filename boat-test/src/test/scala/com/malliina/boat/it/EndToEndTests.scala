package com.malliina.boat.it

import cats.effect.{IO, Resource}
import com.malliina.boat.CoordsEvent
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.client.{DeviceAgent, TcpClient}
import com.malliina.boat.it.EndToEndTests.log
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.io.net.{Network, Socket}
import io.circe.Json
import com.comcast.ip4s.*

import java.net.InetSocketAddress
import scala.concurrent.Promise

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

  http.test("plotter to frontend") { httpClient =>
    val s = server()
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput: Stream[IO, Byte] = Stream.emits(
      sentences.mkString(TcpClient.linefeed).getBytes(TcpClient.charset)
    ) ++ Stream.empty

    val tcpHost = host"127.0.0.1"
    val tcpPort = port"10104"
    val firstMessage = Promise[Json]()
    val coordsPromise = Promise[CoordsEvent]()
    Network[IO]
      .server(port = Option(tcpPort))
      .take(1)
      .evalMap { client =>
//        val c: Socket[IO] = client
//        c.writes
        plotterOutput.through(client.writes).compile.drain
//        client.writes()(plotterOutput).compile.drain
      }
      .compile
      .drain
      .unsafeRunAndForget()
    val serverUrl = s.baseWsUrl.append(reverse.ws.boats.renderString)
    DeviceAgent(BoatConf.anon(tcpHost, tcpPort), serverUrl, httpClient.client).use { agent =>
      agent.connect().map { _ =>
        await(firstMessage.future, 5.seconds)
        agent.close()
      }
    }

    openViewerSocket(httpClient, None) { socket =>
      socket.jsonMessages.map { json =>
        firstMessage.trySuccess(json)
        json.as[CoordsEvent].toOption.foreach { ce =>
          coordsPromise.trySuccess(ce)
        }
      }.compile.drain.unsafeRunAndForget()
      await(firstMessage.future, 5.seconds)
      await(coordsPromise.future, 5.seconds).coords
    }
  }
