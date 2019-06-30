package com.malliina.boat

import com.malliina.boat.ShipType._
import com.malliina.json.PrimitiveFormats
import com.malliina.measure.{DistanceM, SpeedM}
import com.malliina.values.{IdCompanion, StringCompanion, WrappedId, WrappedString}
import play.api.libs.json._

/**
  * @see https://help.marinetraffic.com/hc/en-us/articles/205579997-What-is-the-significance-of-the-AIS-Shiptype-number-
  */
sealed abstract class ShipType(val code: Int) {
  def name(lang: ShipTypesLang): String = {
    val special = lang.special
    this match {
      case WingInGround(_)         => lang.wingInGround
      case SearchAndRescueAircraft => lang.searchAndRescueAircraft
      case Fishing                 => special.fishing
      case Tug(_)                  => special.tug
      case Dredger                 => special.dredger
      case DiveVessel              => special.diveVessel
      case MilitaryOps             => special.militaryOps
      case Sailing                 => special.sailing
      case PleasureCraft           => special.pleasureCraft
      case HighSpeedCraft(_)       => lang.highSpeedCraft
      case PilotVessel             => lang.pilotVessel
      case SearchAndRescue         => lang.searchAndRescue
      case PortTender              => lang.portTender
      case AntiPollution           => lang.antiPollution
      case LawEnforce              => lang.lawEnforce
      case LocalVessel(_)          => lang.localVessel
      case MedicalTransport        => lang.medicalTransport
      case SpecialCraft            => lang.specialCraft
      case Passenger(_)            => lang.passenger
      case Cargo(_)                => lang.cargo
      case Tanker(_)               => lang.tanker
      case Other(_)                => lang.other
      case Unknown(_)              => lang.unknown
    }
  }
}

object ShipType {
  val MaxKnown = 89

  implicit val json = Format[ShipType](
    Reads[ShipType](json => json.validate[Int].map(apply)),
    Writes[ShipType](st => Json.toJson(st.code))
  )

  case class WingInGround(n: Int) extends ShipType(n)
  case object SearchAndRescueAircraft extends ShipType(29)
  case object Fishing extends ShipType(30)
  case class Tug(n: Int) extends ShipType(n)
  case object Dredger extends ShipType(33)
  case object DiveVessel extends ShipType(34)
  case object MilitaryOps extends ShipType(35)
  case object Sailing extends ShipType(36)
  case object PleasureCraft extends ShipType(37)
  case class HighSpeedCraft(n: Int) extends ShipType(n)
  case object PilotVessel extends ShipType(50)
  case object SearchAndRescue extends ShipType(51)
  case object PortTender extends ShipType(53)
  case object AntiPollution extends ShipType(54)
  case object LawEnforce extends ShipType(55)
  case class LocalVessel(n: Int) extends ShipType(n)
  case object MedicalTransport extends ShipType(58)
  case object SpecialCraft extends ShipType(59)
  case class Passenger(n: Int) extends ShipType(n)
  case class Cargo(n: Int) extends ShipType(n)
  case class Tanker(n: Int) extends ShipType(n)
  case class Other(n: Int) extends ShipType(n)
  case class Unknown(n: Int) extends ShipType(n)

  def apply(i: Int): ShipType = i match {
    case n if n >= 20 && n <= 28              => WingInGround(n)
    case 29                                   => SearchAndRescueAircraft
    case 30                                   => Fishing
    case n if (n >= 31 && n <= 32) || n == 52 => Tug(n)
    case 33                                   => Dredger
    case 34                                   => DiveVessel
    case 35                                   => MilitaryOps
    case 36                                   => Sailing
    case 37                                   => PleasureCraft
    case n if n >= 40 && n <= 49              => HighSpeedCraft(n)
    case 50                                   => PilotVessel
    case 51                                   => SearchAndRescue
    case 53                                   => PortTender
    case 54                                   => AntiPollution
    case 55                                   => LawEnforce
    case n if n >= 56 && n <= 57              => LocalVessel(n)
    case 58                                   => MedicalTransport
    case 59                                   => SpecialCraft
    case n if n >= 60 && n <= 69              => Passenger(n)
    case n if n >= 70 && n <= 79              => Cargo(n)
    case n if n >= 80 && n <= 89              => Tanker(n)
    case n if n >= 90 && n <= 99              => Other(n)
    case n                                    => Unknown(n)
  }
}

sealed abstract class PosType(val name: String)

object PosType {

  case object Undefined extends PosType("Undefined")
  case object Gps extends PosType("GPS")
  case object Glonass extends PosType("GLONASS")
  case object GpsGlonass extends PosType("GPS/GLONASS")
  case object LoranC extends PosType("Loran-C")
  case object Chayka extends PosType("Chayka")
  case object Integrated extends PosType("Integrated")
  case object Surveyed extends PosType("Surveyed")
  case object Galileo extends PosType("Galileo")
  case object InternalGNSS extends PosType("Internal GNSS")
  case class Other(i: Int) extends PosType(s"Pos $i")

  def apply(i: Int): PosType = i match {
    case 0  => Undefined
    case 1  => Gps
    case 2  => Glonass
    case 3  => GpsGlonass
    case 4  => LoranC
    case 5  => Chayka
    case 6  => Integrated
    case 7  => Surveyed
    case 8  => Galileo
    case 15 => InternalGNSS
    case n  => Other(n)
  }
}

case class Mmsi(id: Long) extends WrappedId

object Mmsi extends IdCompanion[Mmsi] {
  val Key = "mmsi"
}

case class VesselInfo(mmsi: Mmsi,
                      name: VesselName,
                      shipType: ShipType,
                      coord: Coord,
                      sog: SpeedM,
                      cog: Double,
                      draft: DistanceM,
                      destination: Option[String],
                      heading: Option[Int],
                      eta: Long,
                      timestampMillis: Long,
                      timestampFormatted: FormattedDateTime,
                      time: Timing)

object VesselInfo {
  val HeadingKey = "heading"
  implicit val df = PrimitiveFormats.durationFormat
  implicit val json = Json.format[VesselInfo]
}

sealed trait AISMessage

sealed trait VesselMessage extends AISMessage {
  def mmsi: Mmsi

  def timestamp: Long
}

case class VesselLocation(mmsi: Mmsi,
                          coord: Coord,
                          sog: SpeedM,
                          cog: Double,
                          heading: Option[Int],
                          timestamp: Long)
    extends VesselMessage {
  def toInfo(meta: VesselMetadata, time: Timing) = VesselInfo(
    mmsi,
    meta.name,
    meta.shipType,
    coord,
    sog,
    cog,
    meta.draft,
    meta.destination,
    heading,
    meta.eta,
    timestamp,
    time.dateTime,
    time
  )
}

object VesselLocation {

  import com.malliina.measure.SpeedDoubleM

  implicit val json = Json.format[VesselLocation]
  val readerGeoJson = Reads[VesselLocation] { json =>
    for {
      mmsi <- (json \ "mmsi").validate[Mmsi]
      coordJson <- (json \ "geometry" \ "coordinates").validate[JsValue]
      coord <- Coord.jsonArray.reads(coordJson)
      props <- (json \ "properties").validate[JsObject]
      sog <- (props \ "sog").validate[Double].map { sog =>
        sog.knots
      }
      cog <- (props \ "cog").validate[Double]
      heading <- (props \ "heading").validate[Int].map { h =>
        if (h == 511) None else Option(h)
      }
      timestamp <- (props \ "timestampExternal").validate[Long]
    } yield VesselLocation(mmsi, coord, sog, cog, heading, timestamp)
  }
}

case class VesselName(name: String) extends WrappedString {
  override def value = name
}

object VesselName extends StringCompanion[VesselName] {
  val Key = "name"
}

case class VesselMetadata(name: VesselName,
                          mmsi: Mmsi,
                          timestamp: Long,
                          imo: Long,
                          eta: Long,
                          draft: DistanceM,
                          destination: Option[String],
                          shipType: ShipType,
                          callSign: String)
    extends VesselMessage

object VesselMetadata {
  implicit val df = PrimitiveFormats.durationFormat
  implicit val json = Json.format[VesselMetadata]
  val readerGeoJson = Reads[VesselMetadata] { json =>
    for {
      name <- (json \ "name").validate[VesselName]
      mmsi <- (json \ "mmsi").validate[Mmsi]
      timestamp <- (json \ "timestamp").validate[Long]
      imo <- (json \ "imo").validate[Long]
      eta <- (json \ "eta").validate[Long]
      draft <- (json \ "draught").validate[Int].map { i =>
        DistanceM(i.toDouble / 10)
      }
      destination <- (json \ "destination").validate[String].map { s =>
        if (s.trim.nonEmpty) Option(s.trim) else None
      }
      shipType <- (json \ "shipType").validate[Int].map { i =>
        ShipType(i)
      }
      callSign <- (json \ "callSign").validate[String]
    } yield VesselMetadata(name, mmsi, timestamp, imo, eta, draft, destination, shipType, callSign)
  }
}

case class VesselStatus(json: JsValue) extends AISMessage

object VesselStatus {
  implicit val reader = Reads[VesselStatus] { json =>
    JsSuccess(VesselStatus(json))
  }
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

object StatusTopic {
  def unapply(in: String): Boolean = {
    if (in == "vessels/status") true
    else false
  }
}
