package com.malliina.boat

import com.malliina.boat.MaritimeJson.{nonEmptyOpt, partialReader}
import com.malliina.measure.{DistanceM, SpeedM, DistanceDoubleM, SpeedDoubleM}
import io.circe.*

/** @see
  *   Vesiväyläaineistojen tietosisällön kuvaus
  * @see
  *   https://vayla.fi/documents/20473/38174/Vesiv%C3%A4yl%C3%A4aineistojen+tietosis%C3%A4ll%C3%B6n+kuvaus/68b5f496-19a3-4b3d-887c-971e3366f01e
  */

/** Navigointilaji (NAVL_TYYP)
  */
sealed trait NavMark:
  import NavMark.*
  def translate(in: NavMarkLang) = this match
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

object NavMark:
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

  private val fromInt: PartialFunction[Int, NavMark] =
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
  given Decoder[NavMark] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown nav mark: '$int'.")

/** Rakennetieto (RAKT_TYYP)
  */
enum ConstructionInfo:
  case BuoyBeacon, IceBuoy, BeaconBuoy, SuperBeacon, ExteriorLight, DayBoard, HelicopterPlatform,
    RadioMast, WaterTower, SmokePipe, RadarTower, ChurchTower, SuperBuoy, EdgeCairn, CompassCheck,
    BorderMark
  // rajalinjamerkki
  case BorderLineMark
  // kanavan reunavalo
  case ChannelEdgeLight
  case Tower

  def translate(in: ConstructionLang) = this match
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

object ConstructionInfo:
  private val fromInt: PartialFunction[Int, ConstructionInfo] = {
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
  given Decoder[ConstructionInfo] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown construction type: '$int'.")

/** Turvalaitteen tyyppi (TY_JNR)
  */
enum AidType:
  case Unknown, Lighthouse, SectorLight, LeadingMark, DirectionalLight, MinorLight, OtherMark,
    EdgeMark, RadarTarget, Buoy, Beacon, SignatureLighthouse, Cairn

  def translate(in: AidTypeLang): String = this match
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

object AidType:
  private val fromInt: PartialFunction[Int, AidType] =
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
  given Decoder[AidType] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown aid type: '$int'.")

enum Flotation:
  case Floating, Solid
  case Other(name: String)

  def floatingOrSolid = this == Flotation.Solid || this == Flotation.Floating
  def translate(in: FlotationLang) = this match
    case Flotation.Floating => in.floating
    case Flotation.Solid    => in.solid
    case Flotation.Other(_) => in.other

object Flotation:
  given Decoder[Flotation] = Decoder.decodeString.map:
    case "KELLUVA" => Floating
    // not a typo
    case "KIINTE" => Solid
    case other    => Other(other)

trait Owned:
  def owner: String

  /** @return
    *   a translated name of the owner, best effort
    */
  def ownerName(lang: SpecialWords): String =
    val finnishSpecial = Lang.fi.specialWords
    owner match
      case finnishSpecial.transportAgency               => lang.transportAgency
      case finnishSpecial.transportInfrastructureAgency => lang.transportInfrastructureAgency
      case finnishSpecial.defenceForces                 => lang.defenceForces
      case finnishSpecial.portOfHelsinki                => lang.portOfHelsinki
      case finnishSpecial.cityOfHelsinki                => lang.cityOfHelsinki
      case finnishSpecial.cityOfEspoo                   => lang.cityOfEspoo
      case _                                            => owner

trait SymbolLike extends NameLike:
  def locationFi: Option[String]
  def locationSe: Option[String]
  def location(lang: Lang): Option[String] =
    if lang == Lang.se then locationSe.orElse(locationFi) else locationFi.orElse(locationSe)

sealed trait TrafficMarkType:
  def translate(lang: LimitTypes) = this match
    case TrafficMarkType.SpeedLimit => lang.speedLimit
    case TrafficMarkType.NoWaves    => lang.noWaves
    case TrafficMarkType.Other      => lang.unknown

object TrafficMarkType:
  private val fromInt: PartialFunction[Int, TrafficMarkType] =
    case 6  => NoWaves
    case 11 => SpeedLimit
    case _  => Other
  given Decoder[TrafficMarkType] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown traffic mark type: '$int'.")
  case object SpeedLimit extends TrafficMarkType
  case object NoWaves extends TrafficMarkType
  case object Other extends TrafficMarkType

case class MarineSymbol(
  owner: String,
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
  construction: Option[ConstructionInfo]
) extends SymbolLike
  with Owned

object MarineSymbol:
  private val boolNum: Decoder[Boolean] = Decoder.decodeInt.emap:
    case 0     => Right(false)
    case 1     => Right(true)
    case other => Left(s"Unexpected integer, must be 1 or 0: '$other'.")
  private val boolString: Decoder[Boolean] = Decoder.decodeString.emap:
    case "K"   => Right(true)
    case "E"   => Right(false)
    case other => Left(s"Unexpected string, must be K or E: '$other'.")

  val nonEmpty: Decoder[String] = MaritimeJson.nonEmpty

  implicit val decoder: Decoder[MarineSymbol] = (c: HCursor) =>
    for
      owner <- c.downField("OMISTAJA").as[String]
      topSign <- c.downField("HUIPPUMERK").as[Boolean](using boolNum)
      fasadi <- c.downField("FASADIVALO").as[Boolean](using boolNum)
      nameFi <- c.downField("NIMIS").as[Option[String]](using nonEmptyOpt)
      nameSe <- c.downField("NIMIR").as[Option[String]](using nonEmptyOpt)
      locationFi <- c.downField("SIJAINTIS").as[Option[String]](using nonEmptyOpt)
      locationSe <- c.downField("SIJAINTIR").as[Option[String]](using nonEmptyOpt)
      flotation <- c.downField("SUBTYPE").as[Flotation]
      state <- c.downField("TILA").as[String]
      lit <- c.downField("VALAISTU").as[Boolean](using boolString)
      aidType <- c.downField("TY_JNR").as[AidType]
      navMark <- c.downField("NAVL_TYYP").as[NavMark]
      construction <- c.downField("RAKT_TYYP").as[Option[ConstructionInfo]]
    yield MarineSymbol(
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

case class MinimalMarineSymbol(
  owner: String,
  nameFi: Option[String],
  nameSe: Option[String],
  locationFi: Option[String],
  locationSe: Option[String],
  influence: Option[ZoneOfInfluence],
  trafficMarkType: Option[TrafficMarkType],
  limit: Option[Double],
  extraInfo1: Option[String],
  extraInfo2: Option[String]
) extends SymbolLike
  with Owned:
  def speed: Option[SpeedM] =
    if trafficMarkType.contains(TrafficMarkType.SpeedLimit) then limit.map(_.kmh)
    else None

object MinimalMarineSymbol:
  given Decoder[MinimalMarineSymbol] = (c: HCursor) =>
    for
      owner <- c.downField("OMISTAJA").as[String]
      nameFi <- c.downField("NIMIS").as[Option[String]](using nonEmptyOpt)
      nameSe <- c.downField("NIMIR").as[Option[String]](using nonEmptyOpt)
      locationFi <- c.downField("SIJAINTIS").as[Option[String]](using nonEmptyOpt)
      locationSe <- c.downField("SIJAINTIR").as[Option[String]](using nonEmptyOpt)
      influence <- c.downField("VAIKUTUSAL").as[Option[ZoneOfInfluence]]
      markType <- c.downField("VLM_LAJI").as[Option[TrafficMarkType]]
      limit <- c.downField("RA_ARVO").as[Option[Double]]
      extraInfo1 <- c.downField("LISATIETOS").as[Option[String]](using nonEmptyOpt)
      extraInfo2 <- c.downField("LISATIETOR").as[Option[String]](using nonEmptyOpt)
    yield MinimalMarineSymbol(
      owner,
      nameFi,
      nameSe,
      locationFi,
      locationSe,
      influence,
      markType,
      limit,
      extraInfo1,
      extraInfo2
    )

case class DepthArea(minDepth: DistanceM, maxDepth: DistanceM)

object DepthArea:
  given Decoder[DepthArea] = (c: HCursor) =>
    for
      min <- c.downField("DRVAL1").as[DistanceM]
      max <- c.downField("DRVAL2").as[DistanceM]
    yield DepthArea(min, max)

enum QualityClass(val value: Int):
  case Unknown extends QualityClass(0)
  case One extends QualityClass(1)
  case Two extends QualityClass(2)
  case Three extends QualityClass(3)

object QualityClass:
  val all = Seq(Unknown, One, Two, Three)

  given Decoder[QualityClass] = Decoder.decodeInt.emap: i =>
    all
      .find(_.value == i)
      .map(Right(_))
      .getOrElse(Left(s"Invalid quality class: '$i'."))

enum FairwayType:
  case Navigation, Anchoring, Meetup, HarborPool, Turn, Channel, CoastTraffic, Core, Special, Lock,
    ConfirmedExtra, Helcom, Pilot

  def translate(in: FairwayTypeLang): String = this match
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

object FairwayType:
  private val fromInt: PartialFunction[Int, FairwayType] =
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

  given Decoder[FairwayType] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown fairway type: '$int'.")

enum FairwayState:
  case Confirmed, Aihio, MayChange, ChangeAihio, MayBeRemoved, Removed

object FairwayState:
  private val fromInt: PartialFunction[Int, FairwayState] =
    case 1 => Confirmed
    case 2 => Aihio
    case 3 => MayChange
    case 4 => ChangeAihio
    case 5 => MayBeRemoved
    case 6 => Removed
  given Decoder[FairwayState] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown fairway state: '$int'.")

enum MarkType:
  case Unknown, Lateral, Cardinal

  def translate(in: MarkTypeLang): String = this match
    case Unknown  => in.unknown
    case Lateral  => in.lateral
    case Cardinal => in.cardinal

object MarkType:
  private val fromInt: PartialFunction[Int, MarkType] =
    case 0 => Unknown
    case 1 => Lateral
    case 2 => Cardinal

  given Decoder[MarkType] = Decoder.decodeInt.emap: int =>
    fromInt.lift(int).toRight(s"Unknown mark type: '$int'.")

/** <p>Väyläalue, farledsområde.
  *
  * <p>harrow depth = haraussyvyys
  *
  * @see
  *   Vesiväyläaineistojen tietosisällön kuvaus
  * @see
  *   https://vayla.fi/documents/20473/38174/Vesiv%C3%A4yl%C3%A4aineistojen+tietosis%C3%A4ll%C3%B6n+kuvaus/68b5f496-19a3-4b3d-887c-971e3366f01e
  * @see
  *   http://merisanasto.kyamk.fi/aakkos.php
  */
case class FairwayArea(
  owner: String,
  quality: QualityClass,
  fairwayType: FairwayType,
  fairwayDepth: DistanceM,
  harrowDepth: DistanceM,
  comparisonLevel: String,
  state: FairwayState,
  markType: Option[MarkType]
) extends Owned

object FairwayArea:
  given Decoder[FairwayArea] = (c: HCursor) =>
    for
      owner <- c.downField("OMISTAJA").as[String]
      quality <- c.downField("LAATULK").as[QualityClass]
      fairwayType <- c.downField("VAYALUE_TY").as[FairwayType]
      fairwayDepth <- c.downField("VAYALUE_SY").as[DistanceM]
      harrowDepth <- c.downField("HARAUS_SYV").as[DistanceM]
      comparison <- c.downField("VERT_TASO").as[String]
      state <- c.downField("TILA").as[FairwayState]
      mark <- c.downField("MERK_LAJI").as[Option[MarkType]]
    yield FairwayArea(
      owner,
      quality,
      fairwayType,
      fairwayDepth,
      harrowDepth,
      comparison,
      state,
      mark
    )

enum ZoneOfInfluence:
  case Area, Fairway, AreaAndFairway

  def translate(in: ZonesLang): String = this match
    case Area                    => in.area
    case ZoneOfInfluence.Fairway => in.fairway
    case AreaAndFairway          => in.areaAndFairway

object ZoneOfInfluence:
  given Decoder[ZoneOfInfluence] =
    partialReader[String, ZoneOfInfluence](str => s"Unexpected zone of influence: '$str'."):
      case "A"  => Area
      case "V"  => Fairway
      case "AV" => AreaAndFairway

enum LimitType:
  case SpeedLimit
  case NoWaves
  case NoWindSurfing
  case NoJetSkiing
  case NoMotorPower
  case NoAnchoring
  case NoStopping
  // Kiinnittymiskielto
  case NoAttachment
  case NoOvertaking
  // Kohtaamiskielto
  case NoRendezVous
  case SpeedRecommendation

  def describe(lang: LimitTypes): String = this match
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

object LimitType:
  given Decoder[LimitType] =
    partialReader[String, LimitType](str => s"Unknown limit type: '$str'.")(parse)

  def parse: PartialFunction[String, LimitType] =
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

  def fromString(s: String): Either[String, Seq[LimitType]] =
    val results = s
      .split(", ")
      .toList
      .map: limit =>
        LimitType.parse
          .lift(limit)
          .toRight(s"Unknown limit type: '$limit'.")
    jsonSeq(results)

  private def jsonSeq[T](results: Seq[Either[String, T]]): Either[String, Seq[T]] =
    results.foldLeft[Either[String, Seq[T]]](Right(Nil)): (acc, t) =>
      t.fold(
        err => Left(err),
        ok => acc.map(ts => Seq(ok) ++ ts)
      )

/** @param responsible
  *   merkinnästä vastaava
  * @param publishDate
  *   kohteen julkaisupäivämäärä
  */
case class LimitArea(
  types: Seq[LimitType],
  limit: Option[SpeedM],
  length: Option[DistanceM],
  responsible: Option[String],
  location: Option[String],
  fairwayName: Option[String],
  publishDate: String
):
  def describeTypes(lang: LimitTypes) = types.map(lt => lt.describe(lang))
  def describe(lang: LimitTypes): String = describeTypes(lang).mkString(", ")

  def merge(other: LimitArea): LimitArea = LimitArea(
    (types ++ other.types).distinct,
    (limit.toList ++ other.limit.toList).minByOption(_.toKmh),
    length.orElse(other.length),
    responsible.orElse(other.responsible),
    location.orElse(other.location),
    fairwayName.orElse(other.fairwayName),
    publishDate
  )

object LimitArea:
  given Decoder[LimitArea] = (c: HCursor) =>
    for
      types <-
        c.downField("RAJOITUSTY")
          .as[String]
          .flatMap(s => LimitType.fromString(s).left.map(e => DecodingFailure(e, Nil)))
      limit <- c.downField("SUURUUS").as[Option[Double]].map(_.map(_.kmh))
      length <- c.downField("PITUUS").as[Option[Double]].map(_.map(_.meters))
      responsible <- c.downField("MERK_VAST").as[Option[String]](using nonEmptyOpt)
      location <- c.downField("NIMI_SIJAI").as[Option[String]](using nonEmptyOpt)
      fairwayName <- c.downField("VAY_NIMISU").as[Option[String]](using nonEmptyOpt)
      publishDate <- c.downField("IRROTUS_PV").as[String]
    yield LimitArea(types, limit, length, responsible, location, fairwayName, publishDate)

  def merge(ls: List[LimitArea]): Option[LimitArea] = ls match
    case head :: next => Option(next.fold(head)((acc, more) => acc.merge(more)))
    case Nil          => None
