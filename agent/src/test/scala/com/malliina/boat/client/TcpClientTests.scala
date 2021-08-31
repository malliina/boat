package com.malliina.boat.client

import cats.effect.{Blocker, IO}
import com.malliina.boat.RawSentence
import fs2.io.tcp.SocketGroup
import fs2.{Chunk, Stream}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.Promise

class TcpClientTests extends AsyncSuite {
  val socketsFixture = resource(Blocker[IO].flatMap { b => SocketGroup[IO](b) })

  socketsFixture.test("client receives sentences over TCP socket") { sockets =>
    val sentences = Seq(
      "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
      "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
      "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
      "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
    )
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput: Stream[IO, Array[Byte]] = Stream.emits(
      sentences.map(s => s"$s${TcpClient.crlf}".getBytes(StandardCharsets.US_ASCII)).toList
    ) ++ Stream.never

    // starts pretend-plotter
    val tcpHost = "127.0.0.1"
    val tcpPort = 10103
    val server = sockets.serverResource[IO](new InetSocketAddress(tcpHost, tcpPort)).use[IO, Unit] {
      case (_, clients) =>
        clients.evalMap { client =>
          client.use[IO, Unit] { clientSocket =>
            val byteStream =
              plotterOutput.map(Chunk.bytes).flatMap(c => Stream.emits(c.toList))
            clientSocket.writes()(byteStream).compile.drain
          }
        }.compile.drain
    }
    server.unsafeRunAsyncAndForget()

    // client connects to pretend-plotter
    val p = Promise[RawSentence]()
    Blocker[IO].use { b =>
      TcpClient(tcpHost, tcpPort, b).use { client =>
        client.unsafeConnect()
        client.sentencesHub.map { msg =>
          msg.sentences.headOption.foreach { raw => p.trySuccess(raw) }
        }.compile.drain
      }
    }.unsafeRunSync()
    val received = await(p.future)
    assertEquals(received, sentences.map(RawSentence.apply).head)
  }

  tcpFixture("127.0.0.1", 10109).test("connection to unavailable server fails stream") { tcp =>
    tcp.unsafeConnect()
    intercept[Exception] {
      tcp.sentencesHub.take(1).compile.drain.unsafeRunSync()
    }
  }

  def tcpFixture(host: String, port: Int) = resource(tcpResource(host, port))

  def tcpResource(host: String, port: Int) = for {
    blocker <- Blocker[IO]
    tcp <- TcpClient(host, port, blocker)
  } yield tcp
}
