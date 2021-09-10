package com.malliina.boat.client

import cats.effect.{Blocker, Concurrent, ContextShift, IO, Resource, Timer}
import com.malliina.boat.client.TcpClient.{charset, log}
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.boat.{RawSentence, SentencesMessage}
import fs2.concurrent.Topic
import fs2.io.tcp.{Socket, SocketGroup}
import fs2.{Chunk, Pipe, Pull, Stream, text}

import java.net.InetSocketAddress
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.{ByteBuffer, CharBuffer}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

object TcpClient:
  private val log = Logging(getClass)

  private val crlf = "\r\n"
  private val lf = "\n"
  val linefeed = crlf
  val charset = StandardCharsets.US_ASCII

  // Subscribes to NMEA messages. Depending on device, by default, nothing happens.
  val watchMessage =
    s"${GpsDevice.watchCommand}$linefeed".getBytes(charset)

  def apply(host: String, port: Int, blocker: Blocker, delimiter: String = linefeed)(implicit
    c: Concurrent[IO],
    cs: ContextShift[IO],
    t: Timer[IO]
  ): Resource[IO, TcpClient] = for
    group <- SocketGroup[IO](blocker)
    client <- Resource.eval(apply(host, port, group, delimiter))
  yield client

  def apply(host: String, port: Int, sockets: SocketGroup, delimiter: String)(implicit
    c: Concurrent[IO],
    cs: ContextShift[IO],
    t: Timer[IO]
  ): IO[TcpClient] = for topic <- Topic[IO, SentencesMessage](SentencesMessage(Nil))
  yield new TcpClient(host, port, delimiter, topic, sockets)

class TcpClient(
  host: String,
  port: Int,
  delimiter: String,
  topic: Topic[IO, SentencesMessage],
  group: SocketGroup
)(implicit c: Concurrent[IO], cs: ContextShift[IO], t: Timer[IO]):
  val hostPort = s"tcp://$host:$port"
//  implicit val ec = mat.executionContext
  private val active = new AtomicReference[Option[Socket[IO]]](None)
  private val enabled = new AtomicBoolean(true)
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  val maxBatchSize = 100
  val sendTimeWindow = 500.millis
  val reconnectInterval = 2.second
  val maxLength = RawSentence.MaxLength + 10

  val sentencesHub = topic.subscribe(10).drop(1)

  def unsafeConnect(toServer: Stream[IO, Byte] = Stream.empty): Unit =
    connect(toServer).compile.drain.unsafeRunAsyncAndForget()

  /** Connects to `host:port`. Reconnects on failure.
    *
    * Makes received sentences available in `sentencesHub`. Sends `toServer` to the server.
    */
  def connect(toServer: Stream[IO, Byte] = Stream.empty): Stream[IO, Unit] = connections.flatMap {
    socket =>
      active.set(Option(socket))
      val outMessages = toServer.through(socket.writes())
      val inMessages = socket
        .reads(8192)
        .through(decode[IO](charset))
        .through(text.lines)
        .filter(_.startsWith("$"))
        .map(s => RawSentence(s.trim))
        .groupWithin(maxBatchSize, sendTimeWindow)
        .map(chunk => SentencesMessage(chunk.toList))
      outMessages ++ inMessages.evalMap(m => topic.publish1(m))
  }

  private def connections: Stream[IO, Socket[IO]] =
    Stream
      .resource(group.client(new InetSocketAddress(host, port)))
      .evalTap { socket =>
        IO(log.info(s"Connected to $hostPort."))
      }
      .handleErrorWith { e =>
        if enabled.get() then
          log.warn(s"Lost connection to $hostPort. Reconnecting in $reconnectInterval...", e)
          connections.delayBy(reconnectInterval)
        else {
          log.info(s"Disconnected from $hostPort.")
          Stream.empty
        }
      }

  private def toHostPort(addr: InetSocketAddress) =
    s"${addr.getHostString}:${addr.getPort}"

  def decode[F[_]](charset: Charset): Pipe[F, Byte, String] =
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
          if outputString.isEmpty then Pull.done.as(None)
          else Pull.output1(outputString).as(None)
        case Some((chunk, stream)) =>
          if chunk.nonEmpty then
            val bytes = chunk.toArray
            val byteBuffer = ByteBuffer.wrap(bytes)
            val charBuffer = CharBuffer.allocate(bytes.length * maxCharsPerByte)
            decoder.decode(byteBuffer, charBuffer, false)
            val nextStream = stream.consChunk(Chunk.byteBuffer(byteBuffer.slice()))
            Pull.output1(charBuffer.flip().toString).as(Some(nextStream))
          else Pull.output(Chunk.empty[String]).as(Some(stream))
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

  def close(): Unit =
    enabled.set(false)
    active.get().foreach(_.close.unsafeRunSync())
