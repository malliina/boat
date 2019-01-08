package com.malliina.boat

import com.malliina.values.{IdCompanion, StringCompanion, Wrapped, WrappedId}
import play.api.libs.json._

case class Mmsi(id: Long) extends WrappedId

object Mmsi extends IdCompanion[Mmsi]

sealed trait AISMessage

sealed trait VesselMessage extends AISMessage {
  def mmsi: Mmsi

  def timestamp: Long
}

case class VesselLocation(mmsi: Mmsi,
                          coord: Coord,
                          sog: Double,
                          cog: Double,
                          heading: Int,
                          timestamp: Long) extends VesselMessage

object VesselLocation {
  implicit val format = Json.format[VesselLocation]
  val readerGeoJson = Reads[VesselLocation] { json =>
    for {
      mmsi <- (json \ "mmsi").validate[Mmsi]
      coordJson <- (json \ "geometry" \ "coordinates").validate[JsValue]
      coord <- Coord.jsonArray.reads(coordJson)
      props <- (json \ "properties").validate[JsObject]
      sog <- (props \ "sog").validate[Double]
      cog <- (props \ "cog").validate[Double]
      heading <- (props \ "heading").validate[Int]
      timestamp <- (props \ "timestampExternal").validate[Long]
    } yield VesselLocation(mmsi, coord, sog, cog, heading, timestamp)
  }
}

case class VesselName(name: String) extends Wrapped(name)

object VesselName extends StringCompanion[VesselName]

case class VesselMetadata(name: VesselName,
                          mmsi: Mmsi,
                          timestamp: Long,
                          imo: Long,
                          eta: Long,
                          draught: Long,
                          destination: String,
                          shipType: Int,
                          callSign: String) extends VesselMessage

object VesselMetadata {
  implicit val json = Json.format[VesselMetadata]
}

case class VesselStatus(json: JsValue) extends AISMessage

object VesselStatus {
  implicit val reader = Reads[VesselStatus] { json => JsSuccess(VesselStatus(json)) }
}

object Locations {
  def unapply(in: String): Boolean = {
    if (in.startsWith("vessels/") && in.endsWith("/locations")) true
    else false
  }
}

object Metadata {
  def unapply(in: String): Boolean = {
    if (in.startsWith("vessels/") && in.endsWith("/metadata")) true
    else false
  }
}

object Status {
  def unapply(in: String): Boolean = {
    if (in == "vessels/status") true
    else false
  }
}
