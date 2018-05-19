package com.malliina.boat.client

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString
import com.malliina.boat.{RawSentence, SentencesMessage}
import org.scalatest.FunSuite

import scala.collection.immutable.Iterable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

class AkkaStreamsClientTests extends FunSuite {
  test("TCP client receives and parses messages from TCP server") {
    implicit val system = ActorSystem()
    implicit val mater = ActorMaterializer()
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
    val client = new AkkaStreamsClient(localHost, localPort, out)
    client.connect()
    val expected = SentencesMessage(testSentences.toList.map(RawSentence.apply))
    val actual = await(p1.future)
    assert(expected === actual)
    val actual2 = await(p2.future)
    assert(expected === actual2)
  }

  def await[T](f: Future[T]): T = Await.result(f, 10.seconds)
}
