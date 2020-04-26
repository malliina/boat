package com.malliina.boat.client

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.util.ByteString
import com.malliina.boat.{RawSentence, SentencesMessage}
import play.api.libs.json.Json

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class AkkaStreamsClientTests extends BasicSuite {
  test("kill TCP server".ignore) {
    // this test does not work because the client never gets a termination signal even when the TCP server is shut down
    // TODO devise a fix
    val tcpHost = "127.0.0.1"
    val tcpPort = 10108
    val client = TcpSource(tcpHost, tcpPort, TcpSource.crlf)

    val tcpOut = Source.maybe[ByteString]
    val established = Promise[IncomingConnection]()
    val established2 = Promise[IncomingConnection]()
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      if (!established.trySuccess(conn)) established2.trySuccess(conn)
      val handler =
        Flow.fromSinkAndSourceCoupled(Sink.foreach[ByteString](msg => println(msg)), tcpOut)
      //      conn.flow.joinMat(handler)(Keep.right).run()
      conn.handleWith(handler)
    }

    def startServer() = Tcp().bind(tcpHost, tcpPort).toMat(incomingSink)(Keep.left).run()

    val server = startServer()
    val binding = await(server)
    client.connect()

    await(established.future)
    await(binding.unbind())

    // restores server, awaits reconnection
    val server2 = startServer()
    val binding2 = await(server2)
    await(established2.future, 10.seconds)
    await(binding2.unbind())
  }

  test("TCP client".ignore) {
    val tcpHost = "127.0.0.1"
    val tcpPort = 10110
    val client = TcpSource(tcpHost, tcpPort, TcpSource.crlf)
    try {
      await(client.connect(), 30.seconds)
      client.sentencesHub.runForeach(println)
    } finally client.close()

  }

  test("receives sentences".ignore) {
    val jsons = mutable.Buffer[SentencesMessage]()
    val out = Sink.foreach[SentencesMessage](msg => jsons.append(msg))
    val client = TcpSource("192.168.0.11", 10110)
    try {
      val _ = client.sentencesSource.runWith(out)
      Thread.sleep(20000)
      Files.write(Paths.get("demouiva.json"), Json.toBytes(Json.toJson(jsons)))
    } finally client.close()
  }

  test("count".ignore) {
    val msgs =
      Json.parse(new FileInputStream(Paths.get("demo2.json").toFile)).as[Seq[SentencesMessage]]
    println(msgs.flatMap(_.sentences).length)
  }

  test("TCP client receives and parses messages from TCP server".ignore) {
    val localHost = "127.0.0.1"
    val localPort = 8816
    val testSentences = Iterable("a", "b", "c", "d")
    val server = Tcp().bind(localHost, localPort)
    server.runForeach { conn =>
      val messages = testSentences.map(msg => ByteString(s"$msg\r\n", StandardCharsets.US_ASCII))
      conn.handleWith(Flow.fromSinkAndSource(Sink.ignore, Source[ByteString](messages)))
    }
    val p1 = Promise[SentencesMessage]()
    val p2 = Promise[SentencesMessage]()
    val out = Sink.foreach[SentencesMessage] { msg =>
      val p = if (p1.isCompleted) p2 else p1
      p.trySuccess(msg)
    }
    val client = TcpSource(localHost, localPort)
    client.connect()
    client.sentencesSource.runWith(out)
    val expected = SentencesMessage(testSentences.toList.map(RawSentence.apply))
    val actual = await(p1.future)
    assert(expected == actual)
    val actual2 = await(p2.future)
    assert(expected == actual2)
  }
}
