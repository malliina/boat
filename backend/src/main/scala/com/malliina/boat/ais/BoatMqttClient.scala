package com.malliina.boat.ais

import cats.effect.kernel.{Resource, Temporal}
import cats.effect.IO
import cats.effect.std.Dispatcher
import com.malliina.boat.ais.BoatMqttClient.{AisPair, log, pass, user}
import com.malliina.boat.{AISMessage, AppMode, Locations, Metadata, Mmsi, StatusTopic, TimeFormatter, VesselLocation, VesselMetadata, VesselStatus}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import io.circe.*
import io.circe.parser.decode

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

trait AISSource:
  def slow: Stream[IO, Seq[AisPair]]
  def close: IO[Unit]

object SilentAISSource extends AISSource:
  override val slow: Stream[IO, Seq[AisPair]] = Stream.never[IO]
  override def close: IO[Unit] = IO.pure(())

object BoatMqttClient:
  private val log = AppLogger(getClass)

  val user = "digitraffic"
  val pass = "digitrafficPassword"

  val AllDataTopic = "vessels/#"
  val MetadataTopic = "vessels/+/metadata"

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(enabled: Boolean, mode: AppMode, d: Dispatcher[IO])(implicit
    t: Temporal[IO]
  ): Resource[IO, AISSource] =
    mode match
      case AppMode.Prod if enabled => prod(d)
      case AppMode.Dev if enabled  => prod(d)
      case _                       => Resource.eval(silent())

  def prod(d: Dispatcher[IO])(implicit t: Temporal[IO]): Resource[IO, BoatMqttClient] =
    apply(ProdUrl, AllDataTopic, d)

  def test(d: Dispatcher[IO])(implicit t: Temporal[IO]): Resource[IO, BoatMqttClient] =
    apply(TestUrl, AllDataTopic, d)

  def silent(): IO[AISSource] = IO.delay {
    log.info("AIS is disabled.")
    SilentAISSource
  }

  def apply(url: FullUrl, topic: String, d: Dispatcher[IO])(implicit
    t: Temporal[IO]
  ): Resource[IO, BoatMqttClient] =
    val build = for
      interrupter <- SignallingRef[IO, Boolean](false)
      messagesTopic <- Topic[IO, List[AisPair]]
    yield new BoatMqttClient(url, topic, messagesTopic, interrupter, d)
    for
      client <- Resource.make(build)(_.close)
      // Consumes any messages regardless of whether there's subscribers
      _ <- Stream.emit(()).concurrently(client.publisher).compile.resource.lastOrError
    yield client

  case class AisPair(location: VesselLocation, meta: VesselMetadata):
    def when = Instant.ofEpochMilli(location.timestamp)
    def toInfo(formatter: TimeFormatter) = location.toInfo(meta, formatter.timing(when))

/** Locally caches vessel metadata, then merges it with location data as it is received.
  *
  * @param url
  *   WebSocket URL
  * @param topic
  *   MQTT topic
  */
class BoatMqttClient(
  url: FullUrl,
  topic: String,
  messagesTopic: Topic[IO, List[AisPair]],
  interrupter: SignallingRef[IO, Boolean],
  d: Dispatcher[IO]
)(implicit
  t: Temporal[IO]
) extends AISSource:
  private val metadata = TrieMap.empty[Mmsi, VesselMetadata]

  private val maxBatchSize = 300
  private val sendTimeWindow = 5.seconds
  private val backoffTime = 30.seconds
  private val newStream: Resource[IO, MqttStream] =
    Resource.eval(IO(newClientId())).flatMap { id =>
      MqttStream.resource(MqttSettings(url, id, topic, user, pass), d)
    }
  private val oneConnection: Stream[IO, MqttStream.MqttPayload] =
    Stream.resource(newStream).flatMap { s =>
      s.events
        .handleErrorWith[IO, MqttStream.MqttPayload] { e =>
          Stream
            .eval(IO(log.warn(s"MQTT connection to '$url' failed. Reconnecting...", e)))
            .flatMap { _ =>
              Stream.empty
            }
        }
    }
  private val backoff: Stream[IO, MqttStream.MqttPayload] =
    Stream.eval(IO(log.info(s"Reconnecting to '$url' in $backoffTime..."))) >>
      Stream.sleep(backoffTime) >>
      Stream.empty
  private val parsed: Stream[IO, Either[Error, AISMessage]] =
    (oneConnection ++ backoff).repeat
      .interruptWhen(interrupter)
      .map { msg =>
        val str = msg.payloadString
        msg.topic match
          case Locations()   => decode[VesselLocation](str)(VesselLocation.readerGeoJson)
          case Metadata()    => decode[VesselMetadata](str)(VesselMetadata.readerGeoJson)
          case StatusTopic() => decode[VesselStatus](str)
          case other => Left(DecodingFailure(s"Unknown topic: '$other'. Payload: '$str'.", Nil))
      }
  val vesselMessages = parsed.flatMap {
    case Right(msg) =>
      msg match
        case loc: VesselLocation =>
          metadata
            .get(loc.mmsi)
            .map { meta => Stream(AisPair(loc, meta)) }
            .getOrElse {
              // Drops location updates for which there is no vessel metadata
              Stream.empty
            }
        case vm: VesselMetadata =>
          metadata.update(vm.mmsi, vm)
          Stream.empty
        case _ =>
          Stream.empty
    case Left(_) => Stream.empty
  }
  private val internalMessages =
    vesselMessages.groupWithin(maxBatchSize, sendTimeWindow).map(_.toList)
  val publisher = internalMessages
    .evalMap(list => messagesTopic.publish1(list))

  /** A Source of AIS messages. The "public API" of AIS data.
    */
  val slow: Stream[IO, List[AisPair]] = messagesTopic.subscribe(100)

  private def newClientId() = s"boattracker-${date()}"
  private def date() = Instant.now().toEpochMilli
  override def close: IO[Unit] = interrupter.getAndSet(true).map(_ => ())
