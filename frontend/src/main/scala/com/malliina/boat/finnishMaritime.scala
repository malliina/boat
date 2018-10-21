package com.malliina.boat

import com.malliina.boat.MaritimeJson.intReader
import play.api.libs.json._

sealed trait Translated {
  def fi: String

  def se: String

  def en: String

  def in(lang: Lang): String =
    if (lang == Finnish) fi
    else if (lang == Swedish) se
    else en
}

/**
  * Navigointilaji (NAVL_TYYP)
  */
sealed abstract class NavMark(val fi: String, val se: String, val en: String) extends Translated

object NavMark {

  case object Unknown extends NavMark("Tuntematon", "Okänd", "Unknown")

  case object Left extends NavMark("Vasen", "Vänster", "Left")

  case object Right extends NavMark("Oikea", "Höger", "Right")

  case object North extends NavMark("Pohjois", "Nord", "North")

  case object South extends NavMark("Etelä", "Söder", "South")

  case object West extends NavMark("Länsi", "Väster", "West")

  case object East extends NavMark("Itä", "Ost", "East")

  case object Rock extends NavMark("Karimerkki", "Grund", "Rocks")

  case object SafeWaters extends NavMark("Turvavesimerkki", "Mittledsmärke", "Safe water")

  case object Special extends NavMark("Erikoismerkki", "Specialmärke", "Special mark")

  case object NotApplicable extends NavMark("Ei sovellettavissa", "Inte tillämpbar", "Not applicable")

  implicit val reader: Reads[NavMark] = intReader(json => s"Unknown mark type: '$json'.") {
    case 0 => Unknown
    case 1 => Left
    case 2 => Right
    case 3 => North
    case 4 => South
    case 5 => West
    case 6 => East
    case 7 => Rock
    case 8 => SafeWaters
    case 9 => Special
    case 99 => NotApplicable
  }
}

/**
  * Rakennetieto (RAKT_TYYP)
  */
sealed abstract class ConstructionInfo(val fi: String, val se: String, val en: String) extends Translated

object ConstructionInfo {

  case object BuoyBeacon extends ConstructionInfo("Poijuviitta", "Bojprick", "Buoy beacon")

  case object IceBuoy extends ConstructionInfo("Jääpoiju", "Isboj", "Ice buoy")

  case object BeaconBuoy extends ConstructionInfo("Viittapoiju", "Prickboj", "Beacon buoy")

  case object SuperBeacon extends ConstructionInfo("Suurviitta", "Storprick", "Super beacon")

  case object ExteriorLight extends ConstructionInfo("Fasadivalo", "Ljus", "Exterior light")

  case object DayBoard extends ConstructionInfo("Levykummeli", "Panelkummel", "Dayboard")

  case object HelicopterPlatform extends ConstructionInfo("Helikopteritasanne", "Helikopterplatform", "Helicopter platform")

  case object RadioMast extends ConstructionInfo("Radiomasto", "Radiomast", "Radio mast")

  case object WaterTower extends ConstructionInfo("Vesitorni", "Vattentorn", "Water tower")

  case object SmokePipe extends ConstructionInfo("Savupiippu", "Skorsten", "Chimney")

  case object RadarTower extends ConstructionInfo("Tutkatorni", "Radartorn", "Radar tower")

  case object ChurchTower extends ConstructionInfo("Kirkontorni", "Kyrkotorn", "Church tower")

  case object SuperBuoy extends ConstructionInfo("Suurpoiju", "Storboj", "Super buoy")

  case object EdgeCairn extends ConstructionInfo("Reunakummeli", "Randkummel", "Edge cairn")

  case object CompassCheck extends ConstructionInfo("Kompassintarkistuspaikka", "Kompassplats", "Compass check")

  case object BorderMark extends ConstructionInfo("Rajamerkki", "Gränsmärke", "Border mark")

  // rajalinjamerkki
  case object BorderLineMark extends ConstructionInfo("Rajalinjamerkki", "Gränslinjemärke", "Border line mark")

  // kanavan reunavalo
  case object ChannelEdgeLight extends ConstructionInfo("Kanavan reunavalo", "Kanalens randljus", "Channel edge light")

  case object Tower extends ConstructionInfo("Torni", "Torn", "Tower")

  implicit val reader: Reads[ConstructionInfo] = intReader(json => s"Unknown construction type: '$json'.") {
    case 1 => BuoyBeacon
    case 2 => IceBuoy
    case 4 => BeaconBuoy
    case 5 => SuperBeacon
    case 6 => ExteriorLight
    case 7 => DayBoard
    case 8 => HelicopterPlatform
    case 9 => RadioMast
    case 10 => WaterTower
    case 11 => SmokePipe
    case 12 => RadarTower
    case 13 => ChurchTower
    case 14 => SuperBuoy
    case 15 => EdgeCairn
    case 16 => CompassCheck
    case 17 => BorderMark
    case 18 => BorderLineMark
    case 19 => ChannelEdgeLight
    case 20 => Tower
  }
}

/**
  * Turvalaitteen tyyppi (TY_JNR)
  */
sealed abstract class AidType(val fi: String, val se: String, val en: String) extends Translated

object AidType {

  case object Unknown extends AidType("Tuntematon", "Okänd", "Unknown")

  case object Lighthouse extends AidType("Merimajakka", "Havsfyr", "Lighthouse")

  case object SectorLight extends AidType("Sektoriloisto", "Sektorfyr", "Sector light")

  case object LeadingMark extends AidType("Linjamerkki", "Ensmärke", "Leading mark")

  case object DirectionalLight extends AidType("Suuntaloisto", "Riktning", "Directional light")

  case object MinorLight extends AidType("Apuloisto", "Hjälpfyr", "Minor light")

  case object OtherMark extends AidType("Muu merkki", "Annat märke", "Other mark")

  case object EdgeMark extends AidType("Reunamerkki", "Randmärke", "Edge mark")

  case object RadarTarget extends AidType("Tutkamerkki", "Radarmärke", "Radar target")

  case object Buoy extends AidType("Poiju", "Boj", "Buoy")

  case object Beacon extends AidType("Viitta", "Prick", "Beacon")

  case object SignatureLighthouse extends AidType("Tunnusmajakka", "Båk", "Signature lighthouse")

  case object Cairn extends AidType("Kummeli", "Kummel", "Cairn")

  implicit val reads: Reads[AidType] = intReader[AidType](json => s"Unknown aid type: '$json'.") {
    case 0 => Unknown
    case 1 => Lighthouse
    case 2 => SectorLight
    case 3 => LeadingMark
    case 4 => DirectionalLight
    case 5 => MinorLight
    case 6 => OtherMark
    case 7 => EdgeMark
    case 8 => RadarTarget
    case 9 => Buoy
    case 10 => Beacon
    case 11 => SignatureLighthouse
    case 13 => Cairn
  }

}

sealed abstract class Flotation(val fi: String, val se: String, val en: String) extends Translated

object Flotation {

  case object Floating extends Flotation("Kelluva", "Flytande", "Floating")

  case object Solid extends Flotation("Kiinteä", "Fast", "Solid")

  case class Other(name: String) extends Flotation("Muu", "Annan", "Other")

  implicit val reader: Reads[Flotation] = Reads[Flotation] { json =>
    json.validate[String].map {
      case "KELLUVA" => Floating
      // not a typo
      case "KIINTE" => Solid
      case other => Other(other)
    }
  }
}

case class MarineSymbol(owner: String,
                        exteriorLight: Boolean,
                        topSign: Boolean,
                        nameFi: String,
                        nameSe: String,
                        locationFi: String,
                        locationSe: String,
                        flotation: Flotation,
                        state: String,
                        lit: Boolean,
                        aidType: AidType,
                        navMark: NavMark,
                        construction: Option[ConstructionInfo]) {
  def name(lang: Lang) = if (lang == Swedish) nameSe else nameFi

  def location(lang: Lang) = if (lang == Swedish) locationSe else locationFi
}

/**
  * @see Vesiväyläaineistojen tietosisällön kuvaus
  */
object MarineSymbol {
  val boolNum = Reads[Boolean] { json =>
    json.validate[Int].flatMap {
      case 0 => JsSuccess(false)
      case 1 => JsSuccess(true)
      case other => JsError(s"Unexpected integer, must be 1 or 0: '$other'.")
    }
  }
  val boolString = Reads[Boolean] { json =>
    json.validate[String].flatMap {
      case "K" => JsSuccess(true)
      case "E" => JsSuccess(false)
      case other => JsError(s"Unexpected string, must be K or E: '$other'.")
    }
  }

  implicit val reader = Reads[MarineSymbol] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      topSign <- (json \ "HUIPPUMERK").validate[Boolean](boolNum)
      fasadi <- (json \ "FASADIVALO").validate[Boolean](boolNum)
      nameFi <- (json \ "NIMIR").validate[String]
      nameSe <- (json \ "NIMIS").validate[String]
      locationFi <- (json \ "SIJAINTIS").validate[String]
      locationSe <- (json \ "SIJAINTIR").validate[String]
      flotation <- (json \ "SUBTYPE").validate[Flotation]
      state <- (json \ "TILA").validate[String]
      lit <- (json \ "VALAISTU").validate[Boolean](boolString)
      aidType <- (json \ "TY_JNR").validate[AidType]
      navMark <- (json \ "NAVL_TYYP").validate[NavMark]
      construction <- (json \ "RAKT_TYYP").validateOpt[ConstructionInfo]
    } yield {
      MarineSymbol(owner, fasadi, topSign, nameFi, nameSe, locationFi, locationSe, flotation, state, lit, aidType, navMark, construction)
    }
  }
}

object MaritimeJson {
  def intReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
    Reads[T] { json => json.validate[Int].collect(JsonValidationError(onError(json)))(pf) }
}
