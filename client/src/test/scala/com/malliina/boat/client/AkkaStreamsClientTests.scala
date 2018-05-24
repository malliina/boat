package com.malliina.boat.client

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.util.ByteString
import com.malliina.boat.{RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

class AkkaStreamsClientTests extends FunSuite {
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()

  test("client receives sentences over TCP socket") {
    val sentences = Seq(
      "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
      "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
      "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
      "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
    )
    // the client validates maximum frame length, so we must not concatenate multiple sentences
    val plotterOutput = Source(sentences.map(s => ByteString(s"$s${TcpClient.sentenceDelimiter}", StandardCharsets.US_ASCII)).toList)

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
    val client = new TcpClient(tcpHost, tcpPort, clientSink)
    client.connect()
    try {
      val received = await(p.future)
      assert(received.sentences === sentences.map(RawSentence.apply))
    } finally {
      plotter.shutdown()
    }
  }

  ignore("sends sentences") {
    val url = FullUrl.wss("boat.malliina.com", "/ws/boats")
    val socket = new JsonSocket(url, CustomSSLSocketFactory.forHost("boat.malliina.com"), Seq("X-Boat" -> "name"))
    await(socket.initialConnection)
    val in = Json.parse(new FileInputStream(Paths.get("demo.json").toFile)).as[Seq[SentencesMessage]]
    in.foreach { s =>
      socket.sendMessage(s)
    }
    val out = Sink.foreach[SentencesMessage](msg => socket.sendMessage(msg))
    val client = new TcpClient("192.168.0.11", 10110, out)
    client.connect()
    Thread.sleep(5000)
  }

  ignore("receives sentences") {
    val jsons = mutable.Buffer[SentencesMessage]()
    val out = Sink.foreach[SentencesMessage](msg => jsons.append(msg))
    val client = new TcpClient("192.168.0.11", 10110, out)
    client.connect()
    Thread.sleep(20000)
    Files.write(Paths.get("demo.json"), Json.toBytes(Json.toJson(jsons)))
    client.close()
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
    val client = new TcpClient(localHost, localPort, out)
    client.connect()
    val expected = SentencesMessage(testSentences.toList.map(RawSentence.apply))
    val actual = await(p1.future)
    assert(expected === actual)
    val actual2 = await(p2.future)
    assert(expected === actual2)
  }

  def await[T](f: Future[T]): T = Await.result(f, 10.seconds)
}
