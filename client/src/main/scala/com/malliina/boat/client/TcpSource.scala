package com.malliina.boat.client

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Framing, Keep, MergeHub, Sink, Source, Tcp}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.malliina.boat.client.TcpSource.{log, sentenceDelimiter}
import com.malliina.boat.{RawSentence, SentencesMessage}

import scala.concurrent.Future

object TcpSource {
  private val log = Logging(getClass)

  val sentenceDelimiter = "\r\n"

  def apply(host: String, port: Int)(implicit as: ActorSystem, mat: Materializer): TcpSource =
    new TcpSource(host, port)
}

class TcpSource(host: String, port: Int)(implicit as: ActorSystem, mat: Materializer) {
  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis
  val reconnectInterval = 2.second

  // val switch = KillSwitches.shared(s"TCP-$host")

  val (sink, sentencesHub) =
    MergeHub.source[SentencesMessage](perProducerBufferSize = 16384)
      .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.both)
      .run()
  sentencesHub.runWith(Sink.ignore)

  def flow: Flow[ByteString, SentencesMessage, NotUsed] = Flow[ByteString]
    .via(Framing.delimiter(ByteString(sentenceDelimiter), maximumFrameLength = RawSentence.MaxLength + 10))
    .map(bs => RawSentence(bs.decodeString(StandardCharsets.US_ASCII)))
    .groupedWithin(maxBatchSize, sendTimeWindow)
    .map(SentencesMessage.apply)

  def sentencesSource: Source[SentencesMessage, (Future[Tcp.OutgoingConnection], Future[Done])] = {
    val conn = Tcp().outgoingConnection(host, port).via(flow)
    val toPlotter = Source.maybe[ByteString]
    toPlotter.viaMat(conn)(Keep.right).watchTermination()(Keep.both)
  }

  /** Makes received sentences available in `sentencesHub`
    *
    * @return a Future that completes when the client is disabled and the remaining connection completes
    */
  def connect(): Future[Done] = {
    val (connected, disconnected) = sentencesSource.to(sink).run()
    connected.foreach { conn =>
      log.info(s"Connected to ${toHostPort(conn.remoteAddress)}.")
    }
    disconnected.map { d =>
      log.info(s"Disconnected from $host:$port.")
      d
    }.recover { case t =>
      log.warn("TCP connection failed.", t)
      Done
    }.flatMap { done =>
      if (enabled.get()) {
        log.info(s"Reconnecting in $reconnectInterval...")
        after(reconnectInterval, as.scheduler)(connect())
      } else {
        Future.successful(done)
      }
    }
  }

  private def toHostPort(addr: InetSocketAddress) =
    s"${addr.getHostString}:${addr.getPort}"

  def close(): Unit = {
    enabled.set(false)
    //switch.shutdown()
  }
}
