package com.malliina.boat

import com.malliina.boat.MaritimeJson.{intReader, meters}
import com.malliina.measure.Distance
import play.api.libs.json._

object MaritimeJson {

  import com.malliina.measure.DistanceDouble

  val meters = Reads[Distance] { json => json.validate[Double].map(_.meters) }

  def intReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
    Reads[T] { json => json.validate[Int].collect(JsonValidationError(onError(json)))(pf) }
}

sealed trait Translated {
  def fi: String

  def se: String

  def en: String

  def in(lang: Lang): String =
    if (lang == Lang.Finnish) fi
    else if (lang == Lang.Swedish) se
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
                        nameFi: Option[String],
                        nameSe: Option[String],
                        locationFi: Option[String],
                        locationSe: Option[String],
                        flotation: Flotation,
                        state: String,
                        lit: Boolean,
                        aidType: AidType,
                        navMark: NavMark,
                        construction: Option[ConstructionInfo]) {
  def name(lang: Lang): Option[String] =
    if (lang == Lang.Swedish) nameSe.orElse(nameFi) else nameFi.orElse(nameSe)

  def location(lang: Lang): Option[String] =
    if (lang == Lang.Swedish) locationSe.orElse(locationFi) else locationFi.orElse(locationSe)
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
  val nonEmpty = Reads[Option[String]] { json =>
    json.validate[String].map(_.trim).map(s => if (s.nonEmpty) Option(s) else None)
  }

  implicit val reader = Reads[MarineSymbol] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      topSign <- (json \ "HUIPPUMERK").validate[Boolean](boolNum)
      fasadi <- (json \ "FASADIVALO").validate[Boolean](boolNum)
      nameFi <- (json \ "NIMIR").validate[Option[String]](nonEmpty)
      nameSe <- (json \ "NIMIS").validate[Option[String]](nonEmpty)
      locationFi <- (json \ "SIJAINTIS").validate[Option[String]](nonEmpty)
      locationSe <- (json \ "SIJAINTIR").validate[Option[String]](nonEmpty)
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

case class DepthArea(minDepth: Distance, maxDepth: Distance, when: String)

object DepthArea {
  implicit val reader = Reads[DepthArea] { json =>
    for {
      min <- (json \ "MINDEPTH").validate[Distance](meters)
      max <- (json \ "MAXDEPTH").validate[Distance](meters)
      when <- (json \ "IRROTUS_PV").validate[String]
    } yield DepthArea(min, max, when)
  }
}

sealed abstract class QualityClass(val value: Int)

object QualityClass {
  val all = Seq(Unknown, One, Two, Three)

  implicit val reader = Reads[QualityClass] { json =>
    json.validate[Int].flatMap { i =>
      all.find(_.value == i)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid quality class: '$i'."))
    }
  }

  case object Unknown extends QualityClass(0)

  case object One extends QualityClass(1)

  case object Two extends QualityClass(2)

  case object Three extends QualityClass(3)

}

sealed abstract class FairwayType(val fi: String, val se: String, val en: String) extends Translated

object FairwayType {
  implicit val reader: Reads[FairwayType] = intReader[FairwayType](json => s"Unknown fairway type: '$json'.") {
    case 1 => Navigation
    case 2 => Anchoring
    case 3 => Meetup
    case 4 => HarborPool
    case 5 => Turn
    case 6 => Channel
    case 7 => CoastTraffic
    case 8 => Core
    case 9 => Special
    case 10 => Lock
    case 11 => Secured
    case 12 => Helcom
    case 13 => Pilot
  }

  case object Navigation extends FairwayType("Navigointialue", "", "")

  case object Anchoring extends FairwayType("Ankkurointialue", "", "")

  case object Meetup extends FairwayType("Ohitus- ja kohtaamisalue", "", "")

  case object HarborPool extends FairwayType("Satama-allas", "", "")

  case object Turn extends FairwayType("Kääntöallas", "", "")

  case object Channel extends FairwayType("Kanava", "", "")

  case object CoastTraffic extends FairwayType("Rannikkoliikenteen alue", "", "")

  case object Core extends FairwayType("Veneilyn runkoväylä", "", "")

  case object Special extends FairwayType("Erikoisalue", "", "")

  case object Lock extends FairwayType("Sulku", "", "")

  case object Secured extends FairwayType("Varmistettu lisäalue", "", "")

  case object Helcom extends FairwayType("HELCOM-alue", "", "")

  case object Pilot extends FairwayType("Luotsin otto- ja jättöalue", "", "")

}

sealed abstract class FairwayState(val fi: String, val se: String, val en: String) extends Translated

object FairwayState {
  implicit val reader: Reads[FairwayState] = intReader[FairwayState](json => s"Unknown fairway state: '$json'.") {
    case 1 => Confirmed
    case 2 => Aihio
    case 3 => MayChange
    case 4 => ChangeAihio
    case 5 => MayBeRemoved
    case 6 => Removed
  }

  case object Confirmed extends FairwayState("Vahvistettu", "", "")

  case object Aihio extends FairwayState("Aihio", "", "")

  case object MayChange extends FairwayState("Muutoksen alainen", "", "")

  case object ChangeAihio extends FairwayState("Muutosaihio", "", "")

  case object MayBeRemoved extends FairwayState("Poiston alainen", "", "")

  case object Removed extends FairwayState("Poistettu", "", "")

}

sealed abstract class MarkType(val fi: String, val se: String, val en: String) extends Translated

object MarkType {
  implicit val reader: Reads[MarkType] = intReader[MarkType](json => s"Unknown mark type: '$json'.") {
    case 0 => Unknown
    case 1 => Lateral
    case 2 => Cardinal
  }

  case object Unknown extends MarkType("Tuntematon", "Okänd", "Unknown")

  case object Lateral extends MarkType("Lateraali", "Lateral", "Lateral")

  case object Cardinal extends MarkType("Kardinaali", "Kardinal", "Cardinal")

}

/** <p>Väyläalue, farledsområde.
  *
  * <p>harrow depth = haraussyvyys
  *
  * @see Vesiväyläaineistojen tietosisällön kuvaus
  * @see https://julkaisut.liikennevirasto.fi/pdf3/ohje_2011_vesivayliin_liittyvia_fi.pdf
  */
case class FairwayArea(owner: String,
                       quality: QualityClass,
                       fairwayType: FairwayType,
                       fairwayDepth: Distance,
                       harrowDepth: Distance,
                       comparisonLevel: String,
                       state: FairwayState,
                       markType: Option[MarkType])

object FairwayArea {
  implicit val reader: Reads[FairwayArea] = Reads[FairwayArea] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      quality <- (json \ "LAATULK").validate[QualityClass]
      fairwayType <- (json \ "VAYALUE_TY").validate[FairwayType]
      fairwayDepth <- (json \ "VAYALUE_SY").validate[Distance](meters)
      harrowDepth <- (json \ "HARAUS_SYV").validate[Distance](meters)
      comparison <- (json \ "VERT_TASO").validate[String]
      state <- (json \ "TILA").validate[FairwayState]
      mark <- (json \ "MERK_LAJI").validateOpt[MarkType]
    } yield FairwayArea(owner, quality, fairwayType, fairwayDepth, harrowDepth, comparison, state, mark)
  }
}
