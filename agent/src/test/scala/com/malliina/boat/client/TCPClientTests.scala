package com.malliina.boat.client

import cats.effect.{Deferred, IO, Resource}
import com.comcast.ip4s.*
import com.malliina.boat.RawSentence
import com.malliina.util.AppLogger
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Stream}

import java.nio.ByteBuffer

class TCPClientTests extends munit.CatsEffectSuite:
  val log = AppLogger(getClass)

  test("client receives sentences over TCP socket"):
    val sentences = Seq(
      "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
      "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
      "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
      "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
    )
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput: Stream[IO, Array[Byte]] = Stream.emits(
      sentences.map(s => s"$s${TCPClient.linefeed}".getBytes(TCPClient.charset)).toList
    ) ++ Stream.empty

    // starts pretend-plotter
    val tcpHost = host"127.0.0.1"
    val tcpPort = port"10103"
    val network = Network[IO]
    val clientz: Stream[IO, Socket[IO]] = network.server(port = Option(tcpPort)).head
    val headClient: Stream[IO, Unit] = clientz.evalMap: client =>
      val byteStream =
        plotterOutput
          .map(bs => Chunk.byteBuffer(ByteBuffer.wrap(bs)))
          .flatMap(c => Stream.emits(c.toList))
      byteStream.through(client.writes).compile.drain
    val runPlotter = headClient.compile.drain

    // client connects to pretend-plotter
    for
      plotter <- runPlotter.start
      p <- Deferred[IO, RawSentence]
      client <- TCPClient.default[IO](tcpHost, tcpPort)
      _ <- client.connect(Stream.empty).compile.drain.start
      _ <- client.sentencesHub
        .take(1)
        .evalMap(msg => msg.sentences.headOption.fold(IO.unit)(raw => p.complete(raw)))
        .compile
        .drain
      received <- p.get
      _ <- client.close
      _ <- plotter.join
    yield assertEquals(received, sentences.map(RawSentence.apply).head)

  tcpFixture(host"127.0.0.1", port"10109").test("connection to unavailable server fails stream"):
    tcp =>
      for
        _ <- tcp.close
        res <- tcp.connect().compile.toList
      yield assertEquals(List.empty[Unit], res)

  def tcpFixture(host: Host, port: Port) = ResourceFunFixture(
    Resource.eval(TCPClient.default[IO](host, port))
  )
