package com.malliina.boat.client

import cats.effect.unsafe.implicits.global
import cats.effect.kernel.Resource
import cats.effect.{IO, Resource}
import com.comcast.ip4s.*
import com.comcast.ip4s.{Host, Port}
import com.malliina.boat.RawSentence
import com.malliina.util.AppLogger
import fs2.{Chunk, Stream}
import fs2.io.net.{Network, Socket}

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.Promise

class TcpClientTests extends AsyncSuite:
//  val socketsFixture = resource(Blocker[IO].flatMap { b => SocketGroup[IO](b) })
  val log = AppLogger(getClass)

  test("client receives sentences over TCP socket") {
    val sentences = Seq(
      "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
      "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
      "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
      "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
    )
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput: Stream[IO, Array[Byte]] = Stream.emits(
      sentences.map(s => s"$s${TcpClient.linefeed}".getBytes(TcpClient.charset)).toList
    ) ++ Stream.empty

    // starts pretend-plotter
    val tcpHost = host"127.0.0.1"
    val tcpPort = port"10103"
    val network = Network[IO]
    val clientz: Stream[IO, Socket[IO]] = network.server(port = Option(tcpPort)).head
    val headClient: Stream[IO, Unit] = clientz.evalMap { client =>
      val byteStream =
        plotterOutput
          .map(bs => Chunk.byteBuffer(ByteBuffer.wrap(bs)))
          .flatMap(c => Stream.emits(c.toList))
      byteStream.through(client.writes).compile.drain
    }
    headClient.compile.drain.unsafeRunAndForget()

    // client connects to pretend-plotter
    val p = Promise[RawSentence]()
    val client = TcpClient(tcpHost, tcpPort, TcpClient.linefeed).unsafeRunSync()
    client.connect(Stream.empty).compile.drain.unsafeRunAndForget()
    client.sentencesHub
      .take(1)
      .map { msg =>
        msg.sentences.headOption.foreach { raw => p.trySuccess(raw) }
      }
      .compile
      .drain
      .unsafeRunSync()
    val received = await(p.future)
    client.close()
    assertEquals(received, sentences.map(RawSentence.apply).head)
  }

  tcpFixture(host"127.0.0.1", port"10109").test("connection to unavailable server fails stream") {
    tcp =>
      tcp.close()
      assertEquals(List.empty[Unit], tcp.connect().compile.toList.unsafeRunSync())
  }

  def tcpFixture(host: Host, port: Port) = resource(Resource.eval(TcpClient(host, port)))

//  def tcpResource(host: String, port: Int) = for
//    tcp <- TcpClient(host, port)
//  yield tcp
