package com.malliina.boat

import com.malliina.boat.MaritimeJson.{intReader, nonEmptyOpt, partialReader}
import com.malliina.boat.MinimalMarineSymbol.nonEmpty
import com.malliina.measure.{DistanceM, SpeedM}
import play.api.libs.json._

import scala.util.Try

/** Navigointilaji (NAVL_TYYP)
  */
sealed trait NavMark {
  import NavMark._
  def translate(in: NavMarkLang) = this match {
    case Unknown       => in.unknown
    case Left          => in.left
    case Right         => in.right
    case North         => in.north
    case South         => in.south
    case West          => in.west
    case East          => in.east
    case Rock          => in.rock
    case SafeWaters    => in.safeWaters
    case Special       => in.special
    case NotApplicable => in.notApplicable
  }
}

object NavMark {
  case object Unknown extends NavMark
  case object Left extends NavMark
  case object Right extends NavMark
  case object North extends NavMark
  case object South extends NavMark
  case object West extends NavMark
  case object East extends NavMark
  case object Rock extends NavMark
  case object SafeWaters extends NavMark
  case object Special extends NavMark
  case object NotApplicable extends NavMark

  implicit val reader: Reads[NavMark] = intReader(json => s"Unknown mark type: '$json'.") {
    case 0  => Unknown
    case 1  => Left
    case 2  => Right
    case 3  => North
    case 4  => South
    case 5  => West
    case 6  => East
    case 7  => Rock
    case 8  => SafeWaters
    case 9  => Special
    case 99 => NotApplicable
  }
}

/**
  * Rakennetieto (RAKT_TYYP)
  */
sealed trait ConstructionInfo {
  import ConstructionInfo._
  def translate(in: ConstructionLang) = this match {
    case BuoyBeacon         => in.buoyBeacon
    case IceBuoy            => in.iceBuoy
    case BeaconBuoy         => in.beaconBuoy
    case SuperBeacon        => in.superBeacon
    case ExteriorLight      => in.exteriorLight
    case DayBoard           => in.dayBoard
    case HelicopterPlatform => in.helicopterPlatform
    case RadioMast          => in.radioMast
    case WaterTower         => in.waterTower
    case SmokePipe          => in.smokePipe
    case RadarTower         => in.radarTower
    case ChurchTower        => in.churchTower
    case SuperBuoy          => in.superBuoy
    case EdgeCairn          => in.edgeCairn
    case CompassCheck       => in.compassCheck
    case BorderMark         => in.borderMark
    case BorderLineMark     => in.borderLineMark
    case ChannelEdgeLight   => in.channelEdgeLight
    case Tower              => in.tower
  }
}

object ConstructionInfo {
  case object BuoyBeacon extends ConstructionInfo
  case object IceBuoy extends ConstructionInfo
  case object BeaconBuoy extends ConstructionInfo
  case object SuperBeacon extends ConstructionInfo
  case object ExteriorLight extends ConstructionInfo
  case object DayBoard extends ConstructionInfo
  case object HelicopterPlatform extends ConstructionInfo
  case object RadioMast extends ConstructionInfo
  case object WaterTower extends ConstructionInfo
  case object SmokePipe extends ConstructionInfo
  case object RadarTower extends ConstructionInfo
  case object ChurchTower extends ConstructionInfo
  case object SuperBuoy extends ConstructionInfo
  case object EdgeCairn extends ConstructionInfo
  case object CompassCheck extends ConstructionInfo
  case object BorderMark extends ConstructionInfo
  // rajalinjamerkki
  case object BorderLineMark extends ConstructionInfo
  // kanavan reunavalo
  case object ChannelEdgeLight extends ConstructionInfo
  case object Tower extends ConstructionInfo

  implicit val reader: Reads[ConstructionInfo] =
    intReader(json => s"Unknown construction type: '$json'.") {
      case 1  => BuoyBeacon
      case 2  => IceBuoy
      case 4  => BeaconBuoy
      case 5  => SuperBeacon
      case 6  => ExteriorLight
      case 7  => DayBoard
      case 8  => HelicopterPlatform
      case 9  => RadioMast
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
sealed trait AidType {
  import AidType._
  def translate(in: AidTypeLang): String = this match {
    case Unknown             => in.unknown
    case Lighthouse          => in.lighthouse
    case SectorLight         => in.sectorLight
    case LeadingMark         => in.leadingMark
    case DirectionalLight    => in.directionalLight
    case MinorLight          => in.minorLight
    case OtherMark           => in.otherMark
    case EdgeMark            => in.edgeMark
    case RadarTarget         => in.radarTarget
    case Buoy                => in.buoy
    case Beacon              => in.beacon
    case SignatureLighthouse => in.signatureLighthouse
    case Cairn               => in.cairn
  }
}

object AidType {
  case object Unknown extends AidType
  case object Lighthouse extends AidType
  case object SectorLight extends AidType
  case object LeadingMark extends AidType
  case object DirectionalLight extends AidType
  case object MinorLight extends AidType
  case object OtherMark extends AidType
  case object EdgeMark extends AidType
  case object RadarTarget extends AidType
  case object Buoy extends AidType
  case object Beacon extends AidType
  case object SignatureLighthouse extends AidType
  case object Cairn extends AidType

  implicit val reads: Reads[AidType] = intReader[AidType](json => s"Unknown aid type: '$json'.") {
    case 0  => Unknown
    case 1  => Lighthouse
    case 2  => SectorLight
    case 3  => LeadingMark
    case 4  => DirectionalLight
    case 5  => MinorLight
    case 6  => OtherMark
    case 7  => EdgeMark
    case 8  => RadarTarget
    case 9  => Buoy
    case 10 => Beacon
    case 11 => SignatureLighthouse
    case 13 => Cairn
  }

}

sealed trait Flotation {
  def translate(in: FlotationLang) = this match {
    case Flotation.Floating => in.floating
    case Flotation.Solid    => in.solid
    case Flotation.Other(_) => in.other
  }
}

object Flotation {
  case object Floating extends Flotation
  case object Solid extends Flotation
  case class Other(name: String) extends Flotation

  implicit val reader: Reads[Flotation] = Reads[Flotation] { json =>
    json.validate[String].map {
      case "KELLUVA" => Floating
      // not a typo
      case "KIINTE" => Solid
      case other    => Other(other)
    }
  }
}

trait Owned {
  def owner: String

  /**
    * @return a translated name of the owner, best effort
    */
  def ownerName(lang: SpecialWords): String = {
    val finnishSpecial = Lang.fi.specialWords
    owner match {
      case finnishSpecial.transportAgency => lang.transportAgency
      case finnishSpecial.defenceForces   => lang.defenceForces
      case finnishSpecial.portOfHelsinki  => lang.portOfHelsinki
      case finnishSpecial.cityOfHelsinki  => lang.cityOfHelsinki
      case finnishSpecial.cityOfEspoo     => lang.cityOfEspoo
      case _                              => owner
    }
  }
}

trait SymbolLike extends NameLike {
  def locationFi: Option[String]

  def locationSe: Option[String]

  def location(lang: Lang): Option[String] =
    if (lang == Lang.se) locationSe.orElse(locationFi) else locationFi.orElse(locationSe)
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
                        construction: Option[ConstructionInfo])
    extends SymbolLike
    with Owned

/**
  * @see Vesiväyläaineistojen tietosisällön kuvaus
  * @see https://vayla.fi/documents/20473/38174/Vesiv%C3%A4yl%C3%A4aineistojen+tietosis%C3%A4ll%C3%B6n+kuvaus/68b5f496-19a3-4b3d-887c-971e3366f01e
  */
object MarineSymbol {
  val boolNum = Reads[Boolean] { json =>
    json.validate[Int].flatMap {
      case 0     => JsSuccess(false)
      case 1     => JsSuccess(true)
      case other => JsError(s"Unexpected integer, must be 1 or 0: '$other'.")
    }
  }
  val boolString = Reads[Boolean] { json =>
    json.validate[String].flatMap {
      case "K"   => JsSuccess(true)
      case "E"   => JsSuccess(false)
      case other => JsError(s"Unexpected string, must be K or E: '$other'.")
    }
  }

  implicit val reader = Reads[MarineSymbol] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      topSign <- (json \ "HUIPPUMERK").validate[Boolean](boolNum)
      fasadi <- (json \ "FASADIVALO").validate[Boolean](boolNum)
      nameFi <- (json \ "NIMIS").validate[Option[String]](nonEmpty)
      nameSe <- (json \ "NIMIR").validate[Option[String]](nonEmpty)
      locationFi <- (json \ "SIJAINTIS").validate[Option[String]](nonEmpty)
      locationSe <- (json \ "SIJAINTIR").validate[Option[String]](nonEmpty)
      flotation <- (json \ "SUBTYPE").validate[Flotation]
      state <- (json \ "TILA").validate[String]
      lit <- (json \ "VALAISTU").validate[Boolean](boolString)
      aidType <- (json \ "TY_JNR").validate[AidType]
      navMark <- (json \ "NAVL_TYYP").validate[NavMark]
      construction <- (json \ "RAKT_TYYP").validateOpt[ConstructionInfo]
    } yield {
      MarineSymbol(
        owner,
        fasadi,
        topSign,
        nameFi,
        nameSe,
        locationFi,
        locationSe,
        flotation,
        state,
        lit,
        aidType,
        navMark,
        construction
      )
    }
  }
}

case class MinimalMarineSymbol(owner: String,
                               nameFi: Option[String],
                               nameSe: Option[String],
                               locationFi: Option[String],
                               locationSe: Option[String],
                               influence: ZoneOfInfluence)
    extends SymbolLike
    with Owned

object MinimalMarineSymbol {
  val nonEmpty = MaritimeJson.nonEmpty
  implicit val reader: Reads[MinimalMarineSymbol] = Reads[MinimalMarineSymbol] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      nameFi <- (json \ "NIMIR").validate[Option[String]](nonEmpty)
      nameSe <- (json \ "NIMIS").validate[Option[String]](nonEmpty)
      locationFi <- (json \ "SIJAINTIS").validate[Option[String]](nonEmpty)
      locationSe <- (json \ "SIJAINTIR").validate[Option[String]](nonEmpty)
      influence <- (json \ "VAIKUTUSAL").validate[ZoneOfInfluence]
    } yield {
      MinimalMarineSymbol(owner, nameFi, nameSe, locationFi, locationSe, influence)
    }
  }
}

case class DepthArea(minDepth: DistanceM, maxDepth: DistanceM, when: String)

object DepthArea {
  implicit val reader = Reads[DepthArea] { json =>
    for {
      min <- (json \ "MINDEPTH").validate[DistanceM]
      max <- (json \ "MAXDEPTH").validate[DistanceM]
      when <- (json \ "IRROTUS_PV").validate[String]
    } yield DepthArea(min, max, when)
  }
}

sealed abstract class QualityClass(val value: Int)

object QualityClass {
  val all = Seq(Unknown, One, Two, Three)

  implicit val reader = Reads[QualityClass] { json =>
    json.validate[Int].flatMap { i =>
      all
        .find(_.value == i)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid quality class: '$i'."))
    }
  }

  case object Unknown extends QualityClass(0)
  case object One extends QualityClass(1)
  case object Two extends QualityClass(2)
  case object Three extends QualityClass(3)
}

sealed trait FairwayType {
  import FairwayType._
  def translate(in: FairwayTypeLang): String = this match {
    case Navigation     => in.navigation
    case Anchoring      => in.anchoring
    case Meetup         => in.meetup
    case HarborPool     => in.harborPool
    case Turn           => in.turn
    case Channel        => in.channel
    case CoastTraffic   => in.coastTraffic
    case Core           => in.core
    case Special        => in.special
    case Lock           => in.lock
    case ConfirmedExtra => in.confirmedExtra
    case Helcom         => in.helcom
    case Pilot          => in.pilot
  }
}

object FairwayType {
  implicit val reader: Reads[FairwayType] =
    intReader[FairwayType](json => s"Unknown fairway type: '$json'.") {
      case 1  => Navigation
      case 2  => Anchoring
      case 3  => Meetup
      case 4  => HarborPool
      case 5  => Turn
      case 6  => Channel
      case 7  => CoastTraffic
      case 8  => Core
      case 9  => Special
      case 10 => Lock
      case 11 => ConfirmedExtra
      case 12 => Helcom
      case 13 => Pilot
    }

  case object Navigation extends FairwayType
  case object Anchoring extends FairwayType
  case object Meetup extends FairwayType
  case object HarborPool extends FairwayType
  case object Turn extends FairwayType
  case object Channel extends FairwayType
  case object CoastTraffic extends FairwayType
  case object Core extends FairwayType
  case object Special extends FairwayType
  case object Lock extends FairwayType
  case object ConfirmedExtra extends FairwayType
  case object Helcom extends FairwayType
  case object Pilot extends FairwayType
}

sealed trait FairwayState

object FairwayState {
  implicit val reader: Reads[FairwayState] =
    intReader[FairwayState](json => s"Unknown fairway state: '$json'.") {
      case 1 => Confirmed
      case 2 => Aihio
      case 3 => MayChange
      case 4 => ChangeAihio
      case 5 => MayBeRemoved
      case 6 => Removed
    }

  case object Confirmed extends FairwayState
  case object Aihio extends FairwayState
  case object MayChange extends FairwayState
  case object ChangeAihio extends FairwayState
  case object MayBeRemoved extends FairwayState
  case object Removed extends FairwayState
}

sealed trait MarkType {
  import MarkType._
  def translate(in: MarkTypeLang): String = this match {
    case Unknown  => in.unknown
    case Lateral  => in.lateral
    case Cardinal => in.cardinal
  }
}

object MarkType {
  implicit val reader: Reads[MarkType] =
    intReader[MarkType](json => s"Unknown mark type: '$json'.") {
      case 0 => Unknown
      case 1 => Lateral
      case 2 => Cardinal
    }

  case object Unknown extends MarkType
  case object Lateral extends MarkType
  case object Cardinal extends MarkType
}

/** <p>Väyläalue, farledsområde.
  *
  * <p>harrow depth = haraussyvyys
  *
  * @see Vesiväyläaineistojen tietosisällön kuvaus
  * @see https://vayla.fi/documents/20473/38174/Vesiv%C3%A4yl%C3%A4aineistojen+tietosis%C3%A4ll%C3%B6n+kuvaus/68b5f496-19a3-4b3d-887c-971e3366f01e
  * @see http://merisanasto.kyamk.fi/aakkos.php
  */
case class FairwayArea(owner: String,
                       quality: QualityClass,
                       fairwayType: FairwayType,
                       fairwayDepth: DistanceM,
                       harrowDepth: DistanceM,
                       comparisonLevel: String,
                       state: FairwayState,
                       markType: Option[MarkType])
    extends Owned

object FairwayArea {
  implicit val reader: Reads[FairwayArea] = Reads[FairwayArea] { json =>
    for {
      owner <- (json \ "OMISTAJA").validate[String]
      quality <- (json \ "LAATULK").validate[QualityClass]
      fairwayType <- (json \ "VAYALUE_TY").validate[FairwayType]
      fairwayDepth <- (json \ "VAYALUE_SY").validate[DistanceM]
      harrowDepth <- (json \ "HARAUS_SYV").validate[DistanceM]
      comparison <- (json \ "VERT_TASO").validate[String]
      state <- (json \ "TILA").validate[FairwayState]
      mark <- (json \ "MERK_LAJI").validateOpt[MarkType]
    } yield
      FairwayArea(owner, quality, fairwayType, fairwayDepth, harrowDepth, comparison, state, mark)
  }
}

sealed trait ZoneOfInfluence {
  import ZoneOfInfluence._
  def translate(in: ZonesLang): String = this match {
    case Area                    => in.area
    case ZoneOfInfluence.Fairway => in.fairway
    case AreaAndFairway          => in.areaAndFairway
  }
}

object ZoneOfInfluence {
  implicit val reader: Reads[ZoneOfInfluence] =
    partialReader[String, ZoneOfInfluence](json => s"Unexpected zone of influence: '$json'.") {
      case "A"  => Area
      case "V"  => Fairway
      case "AV" => AreaAndFairway
    }

  case object Area extends ZoneOfInfluence
  case object Fairway extends ZoneOfInfluence
  case object AreaAndFairway extends ZoneOfInfluence
}

sealed trait LimitType {
  import LimitType._

  def describe(lang: LimitTypes): String = this match {
    case SpeedLimit          => lang.speedLimit
    case NoWaves             => lang.noWaves
    case NoWindSurfing       => lang.noWindSurfing
    case NoJetSkiing         => lang.noJetSkiing
    case NoMotorPower        => lang.noMotorPower
    case NoAnchoring         => lang.noAnchoring
    case NoStopping          => lang.noStopping
    case NoAttachment        => lang.noAttachment
    case NoOvertaking        => lang.noOvertaking
    case NoRendezVous        => lang.noRendezVous
    case SpeedRecommendation => lang.speedRecommendation
  }
}

object LimitType {
  case object SpeedLimit extends LimitType
  case object NoWaves extends LimitType
  case object NoWindSurfing extends LimitType
  case object NoJetSkiing extends LimitType
  case object NoMotorPower extends LimitType
  case object NoAnchoring extends LimitType
  case object NoStopping extends LimitType
  // Kiinnittymiskielto
  case object NoAttachment extends LimitType
  case object NoOvertaking extends LimitType
  // Kohtaamiskielto
  case object NoRendezVous extends LimitType
  case object SpeedRecommendation extends LimitType

  implicit val reader: Reads[LimitType] =
    partialReader[String, LimitType](json => s"Unknown limit type: '$json'.")(parse)

  def parse: PartialFunction[String, LimitType] = {
    case "01" => SpeedLimit
    case "02" => NoWaves
    case "03" => NoWindSurfing
    case "04" => NoJetSkiing
    case "05" => NoMotorPower
    case "06" => NoAnchoring
    case "07" => NoStopping
    case "08" => NoAttachment
    case "09" => NoOvertaking
    case "10" => NoRendezVous
    case "11" => SpeedRecommendation
  }

  def fromString(s: String): JsResult[Seq[LimitType]] = {
    val results = s.split(", ").map { limit =>
      LimitType.parse
        .lift(limit)
        .fold[JsResult[LimitType]](JsError(s"Unknown limit type: '$limit'."))(JsSuccess(_))
    }
    jsonSeq(results)
  }

  def jsonSeq[T](results: Seq[JsResult[T]]): JsResult[Seq[T]] =
    results.foldLeft[JsResult[Seq[T]]](JsSuccess(Nil)) { (acc, t) =>
      t.fold(
        err => JsError(err),
        ok => acc.map(ts => Seq(ok) ++ ts)
      )
    }
}

/**
  * @param responsible merkinnästä vastaava
  * @param publishDate kohteen julkaisupäivämäärä
  */
case class LimitArea(types: Seq[LimitType],
                     limit: Option[SpeedM],
                     length: Option[DistanceM],
                     responsible: Option[String],
                     location: Option[String],
                     fairwayName: String,
                     publishDate: String) {
  def describeTypes(lang: LimitTypes) = types.map(lt => lt.describe(lang))
  def describe(lang: LimitTypes): String = describeTypes(lang).mkString(", ")
}

object LimitArea {
  import com.malliina.measure.{DistanceDoubleM, SpeedDoubleM}

  val doubleOptFromString = Reads[Option[Double]] { json =>
    json.validateOpt[String].flatMap { opt =>
      opt
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(s => toDouble(s).map(Option(_)))
        .getOrElse(JsSuccess(None))
    }
  }

  def toDouble(s: String) =
    Try(s.toDouble).fold(_ => JsError(s"Invalid number: '$s'."), ok => JsSuccess(ok))

  /** Example:
    *
    * {"MERK_VAST":"",
    * "IRROTUS_PV":"2018-04-29T00:50:49",
    * "RAJOITUSTY":"01, 02",
    * "NIMI_SIJAI":"Tiiliruukinlahti-Rep",
    * "VAY_NIMISU":"Killingholma-Ströms",
    * "OBJECTID":"103984",
    * "PITUUS":"1514",
    * "SUURUUS":"10"}
    */
  implicit val reader = Reads[LimitArea] { json =>
    for {
      types <- (json \ "RAJOITUSTY").validate[String].flatMap(LimitType.fromString)
      limit <- (json \ "SUURUUS").validate[Option[Double]](doubleOptFromString).map(_.map(s => s.kmh))
      length <- (json \ "PITUUS").validate[Option[Double]](doubleOptFromString).map(_.map(_.meters))
      responsible <- nonEmptyOpt(json \ "MERK_VAST")
      location <- nonEmptyOpt(json \ "NIMI_SIJAI")
      fairwayName <- (json \ "VAY_NIMISU").validate[String]
      publishDate <- (json \ "IRROTUS_PV").validate[String]
    } yield LimitArea(types, limit, length, responsible, location, fairwayName, publishDate)
  }
}
