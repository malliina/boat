package com.malliina.boat.client

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.malliina.boat.{RawSentence, SentencesMessage}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, Promise}

object AkkaStreamsClient {
  def apply(host: String, port: Int, out: JsonSocket, as: ActorSystem, mat: Materializer) =
    new AkkaStreamsClient(host, port, Sink.foreach[SentencesMessage](msg => out.sendMessage(msg)))(as, mat)
}

/**
  * @param out destination of sentences, perhaps a WebSocket
  * @see http://www.catb.org/gpsd/NMEA.html
  */
class AkkaStreamsClient(host: String, port: Int, out: Sink[SentencesMessage, Future[Done]])(implicit as: ActorSystem, mat: Materializer) {
  val sentenceDelimiter = "\r\n"
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis
  implicit val ec = mat.executionContext

  val flow: Flow[ByteString, SentencesMessage, NotUsed] = Flow[ByteString]
    .via(Framing.delimiter(ByteString(sentenceDelimiter), maximumFrameLength = RawSentence.MaxLength + 10))
    .map(bs => RawSentence(bs.decodeString(StandardCharsets.US_ASCII)))
    .groupedWithin(maxBatchSize, sendTimeWindow)
    .map(SentencesMessage.apply)
  val conn = Tcp().outgoingConnection(host, port)
  val sentencesSource: Source[SentencesMessage, Promise[Option[ByteString]]] =
    Source.maybe[ByteString].via(conn).via(flow)

  private val enabled = new AtomicBoolean(true)

  def connect(): Future[Done] = sentencesSource.runWith(out).flatMap { done =>
    if (enabled.get()) {
      after(1.second, as.scheduler)(connect())
    } else {
      Future.successful(done)
    }
  }

  def close(): Unit = enabled.set(false)
}
