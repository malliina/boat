package com.malliina.boat.ais

import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.effect.syntax.all.*
import cats.effect.{Async, Ref, Sync}
import cats.syntax.all.*
import com.malliina.boat.ais.BoatMqttClient.{AisPair, initialBackoff, log, pass, user}
import com.malliina.boat.ais.MqttStream.MqttPayload
import com.malliina.boat.{AISMessage, Locations, LocationsV2, Metadata, MetadataV2, Mmsi, MmsiVesselLocation, MmsiVesselMetadata, StatusTopic, TimeFormatter, VesselInfo, VesselLocation, VesselLocationV2, VesselMetadata, VesselMetadataV2, VesselStatus}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import io.circe.*
import io.circe.parser.decode

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait AISSource[F[_]]:
  def slow: Stream[F, Seq[AisPair]]
  def close: F[Unit]

class SilentAISSource[F[_]: Async] extends AISSource[F]:
  val F = Sync[F]
  override val slow: Stream[F, Seq[AisPair]] = Stream.never[F]
  override def close: F[Unit] = F.pure(())

object BoatMqttClient:
  private val log = AppLogger(getClass)

  val user = "digitraffic"
  val pass = "digitrafficPassword"

  val AllDataTopic = "vessels-v2/#"
  val MetadataTopic = "vessels/+/metadata"

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:443", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:443", "/mqtt")

  private val initialBackoff = 30.seconds

  def build[F[_]: Async](enabled: Boolean, d: Dispatcher[F]): Resource[F, AISSource[F]] =
    if enabled then prod(d) else Resource.eval(silent)

  def prod[F[_]: Async](d: Dispatcher[F]): Resource[F, BoatMqttClient[F]] =
    url(ProdUrl, AllDataTopic, d)

  def test[F[_]: Async](d: Dispatcher[F]): Resource[F, BoatMqttClient[F]] =
    url(TestUrl, AllDataTopic, d)

  private def silent[F[_]: Async]: F[AISSource[F]] = Sync[F].delay:
    log.info("AIS is disabled.")
    SilentAISSource[F]

  def url[F[_]: Async](
    url: FullUrl,
    topic: String,
    d: Dispatcher[F]
  ): Resource[F, BoatMqttClient[F]] =
    val build: F[BoatMqttClient[F]] = for
      interrupter <- SignallingRef[F, Boolean](false)
      messagesTopic <- Topic[F, List[AisPair]]
      receiver <- Topic[F, MqttPayload]
      ref <- Ref.of(initialBackoff)
    yield BoatMqttClient(url, topic, messagesTopic, receiver, ref, interrupter, d)
    for
      client <- Resource.make(build)(_.close)
      // Consumes any messages regardless of whether there's subscribers
      _ <- Stream.emit(()).concurrently(client.publisher).compile.resource.lastOrError
    yield client

  case class AisPair(location: MmsiVesselLocation, meta: MmsiVesselMetadata):
    def when = Instant.ofEpochMilli(location.timestamp)
    def toInfo(formatter: TimeFormatter): VesselInfo = location.toInfo(meta, formatter.timing(when))

/** Locally caches vessel metadata, then merges it with location data as it is received.
  *
  * @param url
  *   WebSocket URL
  * @param topic
  *   MQTT topic
  */
class BoatMqttClient[F[_]: Async](
  url: FullUrl,
  topic: String,
  messagesTopic: Topic[F, List[AisPair]],
  receiver: Topic[F, MqttPayload],
  backoff: Ref[F, FiniteDuration],
  interrupter: SignallingRef[F, Boolean],
  d: Dispatcher[F]
) extends AISSource[F]:
  val F = Sync[F]
  private val metadata = TrieMap.empty[Mmsi, MmsiVesselMetadata]
  private val maxBatchSize = 300
  private val sendTimeWindow = 5.seconds
  private val newStream: Resource[F, MqttStream[F]] =
    Resource
      .eval(F.delay(newClientId()))
      .flatMap: id =>
        MqttStream.resource(MqttSettings(url, id, topic, user, pass), d)
  private val events = Stream
    .resource(newStream)
    .flatMap: s =>
      s.events ++ Stream.raiseError(Exception(s"Closed '$url'."))
  private val consumeConnection = events
    .evalMap(payload => receiver.publish1(payload) <* backoff.set(initialBackoff))
    .compile
    .drain
  private val backoffDelay = backoff
    .getAndUpdate(_ * 2)
    .flatTap: reconnectIn =>
      F.delay(log.info(s"Reconnecting to '$url' in $reconnectIn..."))
  private val backoffs = Stream.eval(backoffDelay).repeat
  private val consume = Stream.eval(consumeConnection).attempts(backoffs).rethrow
  private val exponential = receiver.subscribe(10).concurrently(consume).interruptWhen(interrupter)
  private val parsed: Stream[F, Either[Error, AISMessage]] =
    exponential.map: msg =>
      val str = msg.payloadString
      val mmsiResult = msg.mmsi.left.map(e => DecodingFailure(e.message, Nil))
      msg.topic match
        case LocationsV2() =>
          decode[VesselLocationV2](str).flatMap(loc => mmsiResult.map(mmsi => loc.withMmsi(mmsi)))
        case MetadataV2() =>
          decode[VesselMetadataV2](str).flatMap(meta => mmsiResult.map(mmsi => meta.withMmsi(mmsi)))
        case Locations()   => decode[VesselLocation](str)(VesselLocation.readerGeoJson)
        case Metadata()    => decode[VesselMetadata](str)(VesselMetadata.readerGeoJson)
        case StatusTopic() => decode[VesselStatus](str)
        case other => Left(DecodingFailure(s"Unknown topic: '$other'. Payload: '$str'.", Nil))
  val vesselMessages: Stream[F, AisPair] = parsed.flatMap:
    case Right(msg) =>
      msg match
        case locv2: MmsiVesselLocation =>
          // Drops location updates for which there is no vessel metadata
          metadata.get(locv2.mmsi).map(meta => Stream(AisPair(locv2, meta))).getOrElse(Stream.empty)
        case meta: MmsiVesselMetadata =>
          metadata.update(meta.mmsi, meta)
          Stream.empty
        case VesselStatus(_) => Stream.empty
        case other =>
          log.info(s"Ignoring $other.")
          Stream.empty
    case Left(e) =>
      log.warn(s"Failed to decode AIS JSON.", e)
      Stream.empty
  private val internalMessages =
    vesselMessages.groupWithin(maxBatchSize, sendTimeWindow).map(_.toList)
  val publisher = internalMessages
    .evalMap(list => messagesTopic.publish1(list))

  /** A Source of AIS messages. The "public API" of AIS data.
    */
  val slow: Stream[F, List[AisPair]] = messagesTopic.subscribe(100)

  private def newClientId() = s"boattracker-${date()}"
  private def date() = Instant.now().toEpochMilli
  override def close: F[Unit] = interrupter.getAndSet(true).map(_ => ())
