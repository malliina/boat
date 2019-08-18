package com.malliina.boat.client

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, KillSwitches, StreamTcpException}
import akka.util.ByteString
import com.malliina.boat.client.server.BoatConf
import com.malliina.boat.{RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.collection.immutable.Iterable
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class AkkaStreamsClientTests extends FunSuite {
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext

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
    try {
      await(client.connect(), 30.seconds)
      client.sentencesHub.runForeach(println)
    } finally client.close()

  }

  ignore("receives sentences") {
    val jsons = mutable.Buffer[SentencesMessage]()
    val out = Sink.foreach[SentencesMessage](msg => jsons.append(msg))
    val client = TcpSource("192.168.0.11", 10110)
    try {
      val _ = client.sentencesSource.runWith(out)
      Thread.sleep(20000)
      Files.write(Paths.get("demouiva.json"), Json.toBytes(Json.toJson(jsons)))
    } finally client.close()
  }

  ignore("receive-send to dot com") {
    val url = FullUrl.ws("localhost:9000", "/ws/boats")
    val agent = DeviceAgent(BoatConf.anon("192.168.0.11", 10110), url)
    try {
      agent.connect()
      Thread.sleep(30000)
    } finally agent.close()
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
