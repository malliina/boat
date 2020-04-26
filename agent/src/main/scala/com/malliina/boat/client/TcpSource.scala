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
import com.malliina.boat.client.TcpSource.log
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.boat.{RawSentence, SentencesMessage}

import scala.concurrent.Future

object TcpSource {
  private val log = Logging(getClass)

  val crlf = "\r\n"
  val lf = "\n"

  // Subscribes to NMEA messages. Depending on device, by default, nothing happens.
  val watchMessage = ByteString(GpsDevice.watchCommand + TcpSource.crlf, StandardCharsets.US_ASCII)

  def apply(host: String, port: Int, delimiter: String = crlf)(
    implicit as: ActorSystem,
    mat: Materializer
  ): TcpSource =
    new TcpSource(host, port, delimiter)
}

class TcpSource(host: String, port: Int, delimiter: String)(
  implicit as: ActorSystem,
  mat: Materializer
) {
  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis
  val reconnectInterval = 2.second
  val maxLength = RawSentence.MaxLength + 10

  val (sink, sentencesHub) =
    MergeHub
      .source[SentencesMessage](perProducerBufferSize = 16384)
      .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.both)
      .run()
  sentencesHub.runWith(Sink.ignore)

  def flow: Flow[ByteString, SentencesMessage, NotUsed] = Flow[ByteString]
    .via(Framing.delimiter(ByteString(delimiter), maximumFrameLength = 1000))
    .map(bs => RawSentence(bs.decodeString(StandardCharsets.US_ASCII).trim))
    .filter(_.sentence.startsWith("$"))
    .groupedWithin(maxBatchSize, sendTimeWindow)
    .map(SentencesMessage.apply)

  def sentencesSource: Source[SentencesMessage, (Future[Tcp.OutgoingConnection], Future[Done])] = {
    // Sends nothing to the GPS source, only listens
    setupTcp(Source.maybe[ByteString])
  }

  def setupTcp(
    toSource: Source[ByteString, _]
  ): Source[SentencesMessage, (Future[Tcp.OutgoingConnection], Future[Done])] = {
    val conn = Tcp().outgoingConnection(host, port).via(flow).idleTimeout(20.seconds)
    toSource.viaMat(conn)(Keep.right).watchTermination()(Keep.both)
  }

  /** Makes received sentences available in `sentencesHub`
    *
    * @return a Future that completes when the client is disabled and the remaining connection completes
    */
  def connect(toSource: Source[ByteString, _] = Source.maybe[ByteString]): Future[Done] = {
    val (connected, disconnected) = setupTcp(toSource).to(sink).run()
    connected.foreach { conn =>
      log.info(s"Connected to ${toHostPort(conn.remoteAddress)}.")
    }
    disconnected.map { d =>
      log.info(s"Disconnected from $host:$port.")
      d
    }.recover {
      case t =>
        log.warn("TCP connection failed.", t)
        Done
    }.flatMap { done =>
      if (enabled.get()) {
        log.info(s"Reconnecting in $reconnectInterval...")
        after(reconnectInterval, as.scheduler)(connect(toSource))
      } else {
        Future.successful(done)
      }
    }
  }

  private def toHostPort(addr: InetSocketAddress) =
    s"${addr.getHostString}:${addr.getPort}"

  def close(): Unit = {
    enabled.set(false)
  }
}
