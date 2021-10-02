package com.malliina.boat.ais

import cats.effect.unsafe.IORuntime
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.IO
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

  def apply(mode: AppMode, rt: IORuntime)(implicit t: Temporal[IO]): Resource[IO, AISSource] =
    mode match
      case AppMode.Prod => prod(rt)
      case AppMode.Dev  => Resource.pure(silent())

  def prod(rt: IORuntime)(implicit t: Temporal[IO]): Resource[IO, BoatMqttClient] =
    apply(ProdUrl, AllDataTopic, rt)

  def test(rt: IORuntime)(implicit t: Temporal[IO]): Resource[IO, BoatMqttClient] =
    apply(TestUrl, AllDataTopic, rt)

  def silent() = SilentAISSource

  def apply(url: FullUrl, topic: String, rt: IORuntime)(implicit
    t: Temporal[IO]
  ): Resource[IO, BoatMqttClient] =
    val build = for
      interrupter <- SignallingRef[IO, Boolean](false)
      messagesTopic <- Topic[IO, List[AisPair]]
    yield new BoatMqttClient(url, topic, messagesTopic, interrupter, rt)
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
  rt: IORuntime
)(implicit
  t: Temporal[IO]
) extends AISSource:
//  val interrupter = SignallingRef[IO, Boolean](false).unsafeRunSync()
  private val metadata = TrieMap.empty[Mmsi, VesselMetadata]

  private val maxBatchSize = 300
  private val sendTimeWindow = 5.seconds
  def newStream: Resource[IO, MqttStream] = MqttStream(
    MqttSettings(url, newClientId(), topic, user, pass),
    rt
  )

  val oneConnection: Stream[IO, MqttStream.MqttPayload] =
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

  val parsed: Stream[IO, Either[Error, AISMessage]] =
    oneConnection.repeat.interruptWhen(interrupter).map { msg =>
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
