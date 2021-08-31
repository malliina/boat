package com.malliina.boat.client

import cats.effect.{Blocker, Concurrent, ContextShift, IO, Resource, Timer}
import com.malliina.boat.client.TcpClient.log
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.boat.{RawSentence, SentencesMessage}
import fs2.concurrent.Topic
import fs2.io.tcp.{Socket, SocketGroup}
import fs2.{Chunk, Pipe, Pull, Stream, text}

import java.net.InetSocketAddress
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.{ByteBuffer, CharBuffer}
import java.util.concurrent.atomic.AtomicBoolean

object TcpClient {
  private val log = Logging(getClass)

  val crlf = "\r\n"
  val lf = "\n"

  // Subscribes to NMEA messages. Depending on device, by default, nothing happens.
  val watchMessage =
    s"${GpsDevice.watchCommand}${TcpClient.crlf}".getBytes(StandardCharsets.US_ASCII)

  def apply(host: String, port: Int, blocker: Blocker, delimiter: String = crlf)(implicit
    c: Concurrent[IO],
    cs: ContextShift[IO],
    t: Timer[IO]
  ): Resource[IO, TcpClient] = for {
    topic <- Resource.eval(Topic[IO, SentencesMessage](SentencesMessage(Nil)))
    group <- SocketGroup[IO](blocker)
  } yield new TcpClient(host, port, delimiter, topic, group)
}

class TcpClient(
  host: String,
  port: Int,
  delimiter: String,
  topic: Topic[IO, SentencesMessage],
  group: SocketGroup
)(implicit c: Concurrent[IO], cs: ContextShift[IO], t: Timer[IO]) {
  val hostPort = s"tcp://$host:$port"
//  implicit val ec = mat.executionContext
  private val enabled = new AtomicBoolean(true)
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis
  val reconnectInterval = 2.second
  val maxLength = RawSentence.MaxLength + 10

  val sentencesHub = topic.subscribe(10).drop(1)

  def unsafeConnect(): Unit = connect().compile.drain.unsafeRunAsyncAndForget()

  /** Connects to `host:port`. Reconnects on failure.
    *
    * Makes received sentences available in `sentencesHub`. Sends nothing to the server.
    */
  def connect(toServer: Stream[IO, Byte] = Stream.empty): Stream[IO, Unit] = connections.flatMap {
    socket =>
      val outMessages = toServer.through(socket.writes(None))
      val inMessages = socket
        .reads(Int.MaxValue)
        .through(decode[IO](StandardCharsets.US_ASCII))
        .filter(_.startsWith("$"))
        .through(text.lines)
        .map(s => RawSentence(s.trim))
        .groupWithin(maxBatchSize, sendTimeWindow)
        .map(chunk => SentencesMessage(chunk.toList))
      outMessages ++ inMessages.evalMap(m => topic.publish1(m))
  }

  private def connections: Stream[IO, Socket[IO]] =
    Stream
      .resource(group.client(new InetSocketAddress(host, port)))
      .evalTap { _ =>
        IO(log.info(s"Connected to $hostPort."))
      }
      .handleErrorWith { e =>
        log.warn(s"Lost connection to $hostPort. Reconneting in $reconnectInterval...", e)
        if (enabled.get()) connections.delayBy(reconnectInterval)
        else Stream.empty
      }

  /** Makes received sentences available in `sentencesHub`
    *
    * @return a Future that completes when the client is disabled and the remaining connection completes
    */
//  def connect(toSource: Source[ByteString, _] = Source.maybe[ByteString]): IO[Unit] = {
//    log.info(s"Connecting to $host:$port...")
//    val (connected, disconnected) = connect(toSource).to(sink).run()
//    connected.foreach { conn =>
//      log.info(s"Connected to ${toHostPort(conn.remoteAddress)}.")
//    }
//    disconnected.map { d =>
//      log.info(s"Disconnected from $host:$port.")
//      d
//    }.recover {
//      case t =>
//        log.warn("TCP connection failed.", t)
//        Done
//    }.flatMap { done =>
//      if (enabled.get()) {
//        log.info(s"Reconnecting in $reconnectInterval...")
//        after(reconnectInterval, as.scheduler)(connect(toSource))
//      } else {
//        Future.successful(done)
//      }
//    }
//  }

  private def toHostPort(addr: InetSocketAddress) =
    s"${addr.getHostString}:${addr.getPort}"

  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] = {
    val decoder = charset.newDecoder
    val maxCharsPerByte = math.ceil(decoder.maxCharsPerByte().toDouble).toInt
    val avgBytesPerChar = math.ceil(1.0 / decoder.averageCharsPerByte().toDouble).toInt
    val charBufferSize = 128

    _.repeatPull[String] {
      _.unconsN(charBufferSize * avgBytesPerChar, allowFewer = true).flatMap {
        case None =>
          val charBuffer = CharBuffer.allocate(1)
          decoder.decode(ByteBuffer.allocate(0), charBuffer, true)
          decoder.flush(charBuffer)
          val outputString = charBuffer.flip().toString
          if (outputString.isEmpty) Pull.done.as(None)
          else Pull.output1(outputString).as(None)
        case Some((chunk, stream)) =>
          if (chunk.nonEmpty) {
            val bytes = chunk.toArray
            val byteBuffer = ByteBuffer.wrap(bytes)
            val charBuffer = CharBuffer.allocate(bytes.length * maxCharsPerByte)
            decoder.decode(byteBuffer, charBuffer, false)
            val nextStream = stream.consChunk(Chunk.byteBuffer(byteBuffer.slice()))
            Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
          } else {
            Pull.output(Chunk.empty[String]).as(Some(stream))
          }
      }
    }
  }

  //  val (sink, sentencesHub) =
  //    MergeHub
  //      .source[SentencesMessage](perProducerBufferSize = 16384)
  //      .toMat(BroadcastHub.sink(bufferSize = 2048))(Keep.both)
  //      .run()
  //  sentencesHub.runWith(Sink.ignore)

  //  def flow: Flow[ByteString, SentencesMessage, NotUsed] = Flow[ByteString]
  //    .via(Framing.delimiter(ByteString(delimiter), maximumFrameLength = 1000))
  //    .map(bs => RawSentence(bs.decodeString(StandardCharsets.US_ASCII).trim))
  //    .filter(_.sentence.startsWith("$"))
  //    .groupedWithin(maxBatchSize, sendTimeWindow)
  //    .map(SentencesMessage.apply)

  def close(): Unit = {
    enabled.set(false)
  }
}
