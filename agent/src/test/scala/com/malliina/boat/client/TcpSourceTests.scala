package com.malliina.boat.client

import java.nio.charset.StandardCharsets

import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Keep, Sink, Source, Tcp}
import akka.stream.{KillSwitches, StreamTcpException}
import akka.util.ByteString
import com.malliina.boat.{RawSentence, SentencesMessage}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class TcpSourceTests extends BasicSuite {
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
    try {
      client.connect()
      client.sentencesHub.runWith(clientSink)
      val received = await(p.future)
      assert(received.sentences === sentences.map(RawSentence.apply))
    } finally {
      plotter.shutdown()
      client.close()
    }
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

  def await[T](f: Future[T], duration: Duration = 3.seconds): T = Await.result(f, duration)
}
