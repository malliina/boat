package com.malliina.boat.ais

import cats.effect.{Concurrent, IO, Timer}
import com.malliina.boat.ais.BoatMqttClient.{AisPair, log, pass, user}
import com.malliina.boat.{AISMessage, AppMode, Locations, Metadata, Mmsi, StatusTopic, TimeFormatter, VesselLocation, VesselMetadata, VesselStatus}
import com.malliina.http.FullUrl
import com.malliina.util.AppLogger
import fs2.Stream
import fs2.concurrent.{SignallingRef, Topic}
import play.api.libs.json.{JsError, JsResult, JsSuccess, Json}

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

object BoatMqttClient {
  private val log = AppLogger(getClass)

  val user = "digitraffic"
  val pass = "digitrafficPassword"

  val AllDataTopic = "vessels/#"
  val MetadataTopic = "vessels/+/metadata"

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(mode: AppMode)(implicit c: Concurrent[IO], t: Timer[IO]): AISSource = mode match {
    case AppMode.Prod => prod()
    case AppMode.Dev  => silent()
  }

  def prod()(implicit c: Concurrent[IO], t: Timer[IO]): BoatMqttClient =
    apply(ProdUrl, AllDataTopic)

  def test()(implicit c: Concurrent[IO], t: Timer[IO]): BoatMqttClient =
    apply(TestUrl, AllDataTopic)

  def silent() = SilentAISSource

  def apply(url: FullUrl, topic: String)(implicit
    c: Concurrent[IO],
    t: Timer[IO]
  ): BoatMqttClient = new BoatMqttClient(url, topic)

  case class AisPair(location: VesselLocation, meta: VesselMetadata) {
    def when = Instant.ofEpochMilli(location.timestamp)
    def toInfo(formatter: TimeFormatter) = location.toInfo(meta, formatter.timing(when))
  }
}

trait AISSource {
  def slow: fs2.Stream[IO, Seq[AisPair]]
  def close(): Unit
}

object SilentAISSource extends AISSource {
  override val slow: Stream[IO, Seq[AisPair]] = fs2.Stream.never[IO]
  override def close(): Unit = ()
}

/** Locally caches vessel metadata, then merges it with location data as it is received.
  *
  * @param url   WebSocket URL
  * @param topic MQTT topic
  */
class BoatMqttClient(url: FullUrl, topic: String)(implicit c: Concurrent[IO], t: Timer[IO])
  extends AISSource {
  val interrupter = SignallingRef[IO, Boolean](false).unsafeRunSync()
  private val metadata = TrieMap.empty[Mmsi, VesselMetadata]

  private val maxBatchSize = 300
  private val sendTimeWindow = 5.seconds
  def newStream = MqttStream.unsafe(
    MqttSettings(url, newClientId(), topic, user, pass),
    SignallingRef[IO, Boolean](false).unsafeRunSync()
  )
  val oneConnection = newStream.events.handleErrorWith[IO, MqttStream.MqttPayload] { e =>
    Stream.eval(IO(log.warn(s"MQTT connection to '$url' failed. Reconnecting...", e))).flatMap {
      _ => Stream.empty
    }
  }
  val parsed: Stream[IO, JsResult[AISMessage]] =
    oneConnection.repeat.interruptWhen(interrupter).map { msg =>
      val json = Json.parse(msg.payloadString)
      msg.topic match {
        case Locations()   => VesselLocation.readerGeoJson.reads(json)
        case Metadata()    => VesselMetadata.readerGeoJson.reads(json)
        case StatusTopic() => VesselStatus.reader.reads(json)
        case other         => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
      }
    }
  val vesselMessages = parsed.flatMap {
    case JsSuccess(msg, _) =>
      msg match {
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
      }
    case JsError(_) => Stream.empty
  }
  private val internalMessages =
    vesselMessages.groupWithin(maxBatchSize, sendTimeWindow).map(_.toList)
  internalMessages
    .evalMap(list => messagesTopic.publish1(list))
    .compile
    .drain
    .unsafeRunAsyncAndForget()
  val messagesTopic = Topic[IO, List[AisPair]](Nil).unsafeRunSync()

  /** A Source of AIS messages. The "public API" of AIS data.
    */
  val slow: Stream[IO, List[AisPair]] = messagesTopic.subscribe(100)

  private def newClientId() = s"boattracker-${date()}"
  private def date() = Instant.now().toEpochMilli
  override def close(): Unit = interrupter.set(true).unsafeRunSync()
}
