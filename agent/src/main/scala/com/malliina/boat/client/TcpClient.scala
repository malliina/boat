package com.malliina.boat.client

import cats.effect.kernel.{Resource, Temporal}
import cats.effect.{Async, Resource, Sync}
import com.malliina.boat.client.TcpClient.{charset, log}
import com.malliina.boat.client.server.Device.GpsDevice
import com.malliina.boat.{RawSentence, Readables, SentencesMessage}
import com.malliina.util.AppLogger
import com.malliina.values.Readable
import fs2.concurrent.{SignallingRef, Topic}
import fs2.io.net.{Network, Socket}
import fs2.{Stream, text}
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.malliina.values.ErrorMessage

import java.nio.charset.StandardCharsets

object TcpClient:
  private val log = AppLogger(getClass)

  private val crlf = "\r\n"
//  private val lf = "\n"
  val linefeed: String = crlf
  val charset = StandardCharsets.US_ASCII

  implicit val host: Readable[Host] =
    Readables.string.emap(s => Host.fromString(s).toRight(ErrorMessage(s"Invalid host: '$s'.")))
  implicit val port: Readable[Port] =
    Readables.int.emap(i => Port.fromInt(i).toRight(ErrorMessage(s"Invalid port: '$i'.")))

  // Subscribes to NMEA messages. Depending on device, by default, nothing happens.
  val watchMessage =
    s"${GpsDevice.watchCommand}$linefeed".getBytes(charset)

  def default[F[_]: Async: Network](host: Host, port: Port)(implicit
    t: Temporal[F]
  ): F[TcpClient[F]] = for
    topic <- Topic[F, SentencesMessage]
    signal <- SignallingRef[F, Boolean](false)
  yield TcpClient[F](host, port, topic, signal)

class TcpClient[F[_]: Async: Network](
  host: Host,
  port: Port,
  topic: Topic[F, SentencesMessage],
  signal: SignallingRef[F, Boolean]
):
  val hostPort = s"tcp://$host:$port"
  // Sends after maxBatchSize sentences have been collected or every sendTimeWindow, whichever comes first
  private val maxBatchSize = 100
  private val sendTimeWindow = 500.millis
  private val reconnectInterval = 3.second

  val sentencesHub: Stream[F, SentencesMessage] = topic.subscribe(maxQueued = 10)

  /** Connects to `host:port`. Reconnects on failure.
    *
    * Makes received sentences available in `sentencesHub`. Sends `toServer` to the server upon
    * connection.
    */
  def connect(toServer: Stream[F, Byte] = Stream.empty): Stream[F, Unit] = connections.flatMap {
    socket =>
      val outMessages = toServer.through(socket.writes)
      val inMessages = socket.reads
        .through(text.decodeWithCharset(charset))
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

  private def connections: Stream[F, Socket[F]] =
    Stream
      .resource(
        Resource.eval(delay(log.info(s"Connecting to $hostPort..."))) >>
          Network[F].client(SocketAddress(host, port))
      )
      .evalTap { socket =>
        delay(log.info(s"Connected to $hostPort."))
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

  def close: F[Boolean] = signal.getAndSet(true)

  private def delay[T](thunk: => T) = Sync[F].delay(thunk)
