package com.malliina.boat.client

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Framing, Source, Tcp}
import akka.util.ByteString
import com.malliina.boat.client.TcpClient.sentenceDelimiter
import com.malliina.boat.{RawSentence, SentencesMessage}

import scala.concurrent.duration.DurationInt

class TcpSource(host: String, port: Int)(implicit as: ActorSystem) {
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis

  val flow: Flow[ByteString, SentencesMessage, NotUsed] = Flow[ByteString]
    .via(Framing.delimiter(ByteString(sentenceDelimiter), maximumFrameLength = RawSentence.MaxLength + 10))
    .map(bs => RawSentence(bs.decodeString(StandardCharsets.US_ASCII)))
    .groupedWithin(maxBatchSize, sendTimeWindow)
    .map(SentencesMessage.apply)

  def sentencesSource: Source[SentencesMessage, NotUsed] = {
    val conn = Tcp().outgoingConnection(host, port).via(flow)
    val toPlotter = Source.maybe[ByteString]
    toPlotter.via(conn).mapMaterializedValue(_ => NotUsed)
  }
}
