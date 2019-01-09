package com.malliina.boat.ais

import java.nio.charset.StandardCharsets
import java.time.Instant

import akka.Done
import akka.stream.scaladsl.Source
import com.malliina.boat.{AISMessage, Locations, Metadata, Mmsi, Status, VesselLocation, VesselMessage, VesselMessages, VesselMetadata, VesselStatus}
import com.malliina.http.FullUrl
import play.api.Logger
import play.api.libs.json.{JsError, JsResult, JsSuccess, Json}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object MqClient {
  private val log = Logger(getClass)

  val AllDataTopic = "vessels/#"
  val MetadataTopic = "vessels/+/metadata"

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(): MqClient = apply(TestUrl, AllDataTopic)

  def apply(url: FullUrl, topic: String): MqClient = new MqClient(url, topic)
}

class MqClient(url: FullUrl, topic: String) {
  private val metadata = TrieMap.empty[Mmsi, VesselMetadata]

  val maxBatchSize = 100
  val sendTimeWindow = 2.seconds

  val date = Instant.now().toEpochMilli
  val settings = MqttSettings(url, s"boattracker-$date", topic, "digitraffic", "digitrafficPassword")
  val graph = MqttGraph(settings)
  val src = MqttSource(settings)
  val parsed: Source[JsResult[AISMessage], Future[Done]] = src.map { msg =>
    val json = Json.parse(msg.payload.decodeString(StandardCharsets.UTF_8))
    msg.topic match {
      case Locations() => VesselLocation.readerGeoJson.reads(json)
      case Metadata() => VesselMetadata.readerGeoJson.reads(json)
      case Status() => VesselStatus.reader.reads(json)
      case other => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
    }
  }
  val vesselMessages = parsed.flatMapConcat {
    case JsSuccess(msg, _) => msg match {
      case loc: VesselLocation =>
        metadata.get(loc.mmsi).map { meta =>
          Source.single(loc.toInfo(meta))
        }.getOrElse {
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
  val slow: Source[VesselMessages, Future[Done]] =
    vesselMessages.groupedWithin(maxBatchSize, sendTimeWindow).map(VesselMessages.apply)
}
