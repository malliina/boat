package com.malliina.boat.client

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, KillSwitches, StreamTcpException}
import akka.util.ByteString
import com.malliina.boat.{RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future, Promise}

class AkkaStreamsClientTests extends FunSuite {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext

  test("client receives sentences over TCP socket") {
    val sentences = Seq(
      "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
      "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
      "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
      "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
    )
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpSource.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

    // pretend-plotter
    val tcpHost = "127.0.0.1"
    val tcpPort = 10103
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      conn.flow.runWith(plotterOutput, Sink.foreach(msg => println(msg)))
    }
    val plotter = Tcp().bind(tcpHost, tcpPort).viaMat(KillSwitches.single)(Keep.right).toMat(incomingSink)(Keep.left).run()

    // client connects to plotter
    val p = Promise[SentencesMessage]()
    val clientSink = Sink.foreach[SentencesMessage] { msg =>
      p.trySuccess(msg)
    }
    val client = TcpSource(tcpHost, tcpPort)
    client.connect()
    client.sentencesHub.runWith(clientSink)
    try {
      val received = await(p.future)
      assert(received.sentences === sentences.map(RawSentence.apply))
    } finally {
      plotter.shutdown()
      client.close()
    }
  }

  ignore("kill TCP server") {
    // this test does not work because the client never gets a termination signal even when the TCP server is shut down
    // TODO devise a fix
    val tcpHost = "127.0.0.1"
    val tcpPort = 10108
    val client = TcpSource(tcpHost, tcpPort)

    val tcpOut = Source.maybe[ByteString]
    val established = Promise[IncomingConnection]()
    val established2 = Promise[IncomingConnection]()
    val incomingSink = Sink.foreach[IncomingConnection] { conn =>
      if (!established.trySuccess(conn)) established2.trySuccess(conn)
      val handler = Flow.fromSinkAndSourceCoupled(Sink.foreach[ByteString](msg => println(msg)), tcpOut)
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

  ignore("TCP client") {
    val tcpHost = "127.0.0.1"
    val tcpPort = 10110
    val client = TcpSource(tcpHost, tcpPort)
    val done = client.connect()
    await(done, 30.seconds)
  }

  test("connection to unavailable server fails stream") {
    val tcpHost = "127.0.0.1"
    val tcpPort = 10109
    val client = TcpSource(tcpHost, tcpPort)
    val src = client.sentencesSource.watchTermination()(Keep.right).map(identity)
    val completion = src.toMat(Sink.foreach(msg => println(msg)))(Keep.left).run()
    try {
      intercept[StreamTcpException] {
        await(completion)
      }
    } finally {
      client.close()
    }
  }

  ignore("receives sentences") {
    val jsons = mutable.Buffer[SentencesMessage]()
    val out = Sink.foreach[SentencesMessage](msg => jsons.append(msg))
    val client = TcpSource("192.168.0.11", 10110)
    val _ = client.sentencesSource.runWith(out)
    Thread.sleep(50000)
    Files.write(Paths.get("demo2.json"), Json.toBytes(Json.toJson(jsons)))
    //client.close()
  }

  ignore("receive-send locally") {
    val url = FullUrl.ws("localhost:9000", "/ws/boats")
    val agent = BoatAgent("192.168.0.11", 10110, url)
    agent.connect()
    Thread.sleep(300000)
  }

  ignore("count") {
    val msgs = Json.parse(new FileInputStream(Paths.get("demo2.json").toFile)).as[Seq[SentencesMessage]]
    println(msgs.flatMap(_.sentences).length)
  }

  ignore("TCP client receives and parses messages from TCP server") {
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
    assert(expected === actual)
    val actual2 = await(p2.future)
    assert(expected === actual2)
  }

  def await[T](f: Future[T], duration: Duration = 3.seconds): T = Await.result(f, duration)
}
