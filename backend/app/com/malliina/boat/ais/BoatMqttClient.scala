package com.malliina.boat.ais

import java.nio.charset.StandardCharsets
import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.{RestartSource, Source}
import com.malliina.boat.ais.BoatMqttClient.{AisPair, log, pass, user}
import com.malliina.boat.{AISMessage, Locations, Metadata, Mmsi, StatusTopic, TimeFormatter, VesselLocation, VesselMetadata, VesselStatus}
import com.malliina.http.FullUrl
import play.api.libs.json.{JsError, JsResult, JsSuccess, Json}
import play.api.{Logger, Mode}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

object BoatMqttClient {
  private val log = Logger(getClass)

  val user = "digitraffic"
  val pass = "digitrafficPassword"

  val AllDataTopic = "vessels/#"
  val MetadataTopic = "vessels/+/metadata"

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(mode: Mode): BoatMqttClient =
    if (mode == Mode.Prod) prod() else test()

  def prod() = apply(ProdUrl, AllDataTopic)

  def test() = apply(TestUrl, AllDataTopic)

  def apply(url: FullUrl, topic: String): BoatMqttClient = new BoatMqttClient(url, topic)

  case class AisPair(location: VesselLocation, meta: VesselMetadata) {
    def when = Instant.ofEpochMilli(location.timestamp)
    def toInfo(formatter: TimeFormatter) = location.toInfo(meta, formatter.formatDateTime(when))
  }
}

/** Locally caches vessel metadata, then merges it with location data as it is received.
  *
  * @param url   WebSocket URL
  * @param topic MQTT topic
  */
class BoatMqttClient(url: FullUrl, topic: String) {
  private val metadata = TrieMap.empty[Mmsi, VesselMetadata]

  private val maxBatchSize = 300
  private val sendTimeWindow = 5.seconds
  private val settings = MqttSettings(url, newClientId, topic, user, pass)
  private val src = RestartSource.onFailuresWithBackoff(
    minBackoff = 5.seconds,
    maxBackoff = 12.hours,
    randomFactor = 0.2) { () =>
    log.info(s"Starting MQTT source at '${settings.broker}'...")
    MqttSource(settings.copy(clientId = newClientId))
  }
  val parsed: Source[JsResult[AISMessage], NotUsed] = src.map { msg =>
    val json = Json.parse(msg.payload.decodeString(StandardCharsets.UTF_8))
    msg.topic match {
      case Locations() => VesselLocation.readerGeoJson.reads(json)
      case Metadata() => VesselMetadata.readerGeoJson.reads(json)
      case StatusTopic() => VesselStatus.reader.reads(json)
      case other => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
    }
  }
  val vesselMessages = parsed.flatMapConcat {
    case JsSuccess(msg, _) => msg match {
      case loc: VesselLocation =>
        metadata.get(loc.mmsi).map { meta =>
          Source.single(AisPair(loc, meta))
        }.getOrElse {
          // Drops location updates for which there is no vessel metadata
          Source.empty
        }
      case vm: VesselMetadata =>
        metadata.update(vm.mmsi, vm)
        Source.empty
      case _ =>
        Source.empty
    }
    case JsError(_) => Source.empty
  }

  val slow: Source[Seq[AisPair], NotUsed] =
    vesselMessages.groupedWithin(maxBatchSize, sendTimeWindow)

  private def newClientId = s"boattracker-$date"

  private def date = Instant.now().toEpochMilli
}
