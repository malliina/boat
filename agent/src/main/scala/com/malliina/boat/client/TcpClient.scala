package com.malliina.boat.client

import cats.effect.kernel.{Concurrent, Resource, Temporal}
import cats.effect.{Concurrent, IO, Resource}
import com.malliina.boat.client.TcpClient.{charset, log}
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.boat.{RawSentence, Readable, SentencesMessage}
import com.malliina.util.AppLogger
import fs2.concurrent.{SignallingRef, Topic}
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Pipe, Pull, Stream, text}
import cats.effect.MonadCancelThrow
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.malliina.boat.Readable.from
import com.malliina.values.ErrorMessage

import java.net.InetSocketAddress
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.{ByteBuffer, CharBuffer}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

object TcpClient:
  private val log = AppLogger(getClass)

  private val crlf = "\r\n"
  private val lf = "\n"
  val linefeed: String = crlf
  val charset = StandardCharsets.US_ASCII

  implicit val host: Readable[Host] =
    from[String, Host](s => Host.fromString(s).toRight(ErrorMessage(s"Invalid host: '$s'.")))
  implicit val port: Readable[Port] =
    from[Int, Port](i => Port.fromInt(i).toRight(ErrorMessage(s"Invalid port: '$i'.")))

  // Subscribes to NMEA messages. Depending on device, by default, nothing happens.
  val watchMessage =
    s"${GpsDevice.watchCommand}$linefeed".getBytes(charset)

  def default(host: Host, port: Port, delimiter: String = linefeed)(implicit
    t: Temporal[IO]
  ): IO[TcpClient] = for
    topic <- Topic[IO, SentencesMessage]
    signal <- SignallingRef[IO, Boolean](false)
  yield TcpClient(host, port, delimiter, topic, signal)

class TcpClient(
  host: Host,
  port: Port,
  delimiter: String,
  topic: Topic[IO, SentencesMessage],
  signal: SignallingRef[IO, Boolean]
)(implicit t: Temporal[IO]):
  val hostPort = s"tcp://$host:$port"
  private val active = new AtomicReference[Option[Socket[IO]]]
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  private val maxBatchSize = 100
  private val sendTimeWindow = 500.millis
  private val reconnectInterval = 2.second

  val sentencesHub: Stream[IO, SentencesMessage] = topic.subscribe(maxQueued = 10)

  /** Connects to `host:port`. Reconnects on failure.
    *
    * Makes received sentences available in `sentencesHub`. Sends `toServer` to the server.
    */
  def connect(toServer: Stream[IO, Byte] = Stream.empty): Stream[IO, Unit] = connections.flatMap {
    socket =>
      active.set(Option(socket))
      val outMessages = toServer.through(socket.writes)
      val inMessages = socket.reads
        .through(decode[IO](charset))
        .through(text.lines)
        .filter(_.startsWith("$"))
        .map(s => RawSentence(s.trim))
        .groupWithin(maxBatchSize, sendTimeWindow)
        .map(chunk => SentencesMessage(chunk.toList))
      outMessages ++ inMessages.evalMap { m =>
        topic.publish1(m).map { e =>
          e.fold(
            closed => log.warn(s"Topic closed, failed to publish '$m' from $hostPort."),
            identity
          )
        }
      }
  }

  private def connections: Stream[IO, Socket[IO]] =
    Stream
      .resource(Network[IO].client(SocketAddress(host, port)))
      .evalTap { socket =>
        IO(log.info(s"Connected to $hostPort."))
      }
      .handleErrorWith { e =>
        Stream.eval(signal.get).flatMap { isDisabled =>
          if !isDisabled then
            log.warn(s"Lost connection to $hostPort. Reconnecting in $reconnectInterval...", e)
            connections.delayBy(reconnectInterval)
          else
            log.info(s"Disconnected from $hostPort.")
            Stream.empty
        }
      }
      .interruptWhen(signal)

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

  def close: IO[Boolean] = signal.getAndSet(true)
