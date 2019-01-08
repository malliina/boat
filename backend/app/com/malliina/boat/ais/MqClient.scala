package com.malliina.boat.ais

import java.nio.charset.StandardCharsets
import java.time.Instant

import akka.Done
import akka.stream.scaladsl.Source
import com.malliina.boat.{AISMessage, Locations, Metadata, Status, VesselLocation, VesselMessage, VesselMessages, VesselMetadata, VesselStatus}
import com.malliina.http.FullUrl
import play.api.Logger
import play.api.libs.json.{JsError, JsResult, JsSuccess, Json}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object MqClient {
  private val log = Logger(getClass)

  val TestUrl = FullUrl.wss("meri-test.digitraffic.fi:61619", "/mqtt")
  val ProdUrl = FullUrl.wss("meri.digitraffic.fi:61619", "/mqtt")

  def apply(): MqClient = apply(TestUrl)

  def apply(url: FullUrl): MqClient = new MqClient(url)
}

class MqClient(url: FullUrl) {
  val maxBatchSize = 100
  val sendTimeWindow = 2.seconds

  val date = Instant.now().toEpochMilli
  val settings = MqttSettings(url, s"boattracker-$date", "vessels/#", "digitraffic", "digitrafficPassword")
  val graph = MqttGraph(settings)
  val src = MqttSource(settings)
  val parsed: Source[JsResult[AISMessage], Future[Done]] = src.map { msg =>
    val json = Json.parse(msg.payload.decodeString(StandardCharsets.UTF_8))
    msg.topic match {
      case Locations() => VesselLocation.readerGeoJson.reads(json)
      case Metadata() => VesselMetadata.json.reads(json)
      case Status() => VesselStatus.reader.reads(json)
      case other => JsError(s"Unknown topic: '$other'. JSON: '$json'.")
    }
  }
  val vesselMessages = parsed.flatMapConcat {
    case JsSuccess(msg, _) => msg match {
      case vm: VesselMessage => Source.single(vm)
      case _ => Source.empty
    }
    case JsError(_) => Source.empty
  }
  val slow: Source[VesselMessages, Future[Done]] =
    vesselMessages.groupedWithin(maxBatchSize, sendTimeWindow).map(VesselMessages.apply)
}
