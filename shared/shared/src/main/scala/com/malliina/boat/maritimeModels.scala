package com.malliina.boat

import com.malliina.boat.MaritimeJson.doubleReader
import com.malliina.measure.DistanceM
import play.api.libs.json._

trait NameLike {
  def nameFi: Option[String]

  def nameSe: Option[String]

  def name(lang: Lang): Option[String] =
    if (lang == Lang.se) nameSe.orElse(nameFi) else nameFi.orElse(nameSe)
}

sealed trait FairwayLighting

object FairwayLighting {
  implicit val reader: Reads[FairwayLighting] =
    doubleReader(json => s"Unknown fairway lighting: '$json'.") {
      case 0 => UnknownLighting
      case 1 => Lighting
      case 2 => NoLighting
    }
  case object NoLighting extends FairwayLighting
  case object Lighting extends FairwayLighting
  case object UnknownLighting extends FairwayLighting

  def fromInt(i: Int): FairwayLighting = i match {
    case 1 => Lighting
    case 2 => NoLighting
    case _ => UnknownLighting
  }

  def toInt(l: FairwayLighting): Int = l match {
    case UnknownLighting => 0
    case Lighting        => 1
    case NoLighting      => 2
  }
}

sealed trait FairwaySeaType

object FairwaySeaType {
  implicit val reader: Reads[FairwaySeaType] =
    doubleReader(json => s"Unknown fairway sea type: '$json'.") {
      case 1 => SeaFairway
      case 2 => InnerFairway
    }
  // Meriväylä
  case object SeaFairway extends FairwaySeaType
  // Sisävesiväylä
  case object InnerFairway extends FairwaySeaType
  // Virtual object for db interface
  case object UnknownFairway extends FairwaySeaType

  def toInt(fst: FairwaySeaType) = fst match {
    case SeaFairway     => 1
    case InnerFairway   => 2
    case UnknownFairway => 0
  }

  def fromInt(i: Int) = i match {
    case 1 => SeaFairway
    case 2 => InnerFairway
    case _ => UnknownFairway
  }
}

sealed abstract class SeaArea(val value: Int)

object SeaArea {
  implicit val reader: Reads[SeaArea] = Reads[SeaArea] { json =>
    json.validate[Double].map { d =>
      fromIntOrOther(d.toInt)
    }
  }
  val all: Seq[SeaArea] = Seq(
    Unknown,
    Perameri,
    Selkameri,
    Ahvenanmeri,
    Saaristomeri,
    Suomenlahti,
    Itameri,
    Saimaa,
    Paijanne,
    Kokemaenjoki,
    Oulujarvi,
    SotkamonJarvet,
    KuhmonJarvet,
    KuusamonJarvet,
    Kiantajarvi,
    Simojarvi,
    LokkaPorttipahta,
    Kemijarvi,
    Inarinjarvi,
    Nitsijarvi,
    Miekkojarvi,
    Tornionjoki,
    Ahtarinjarvi,
    Lappajarvi,
    Pyhajarvi,
    Lohjanjarvi
  )

  def fromInt(i: Int) = all.find(_.value == i).toRight(s"Unknown sea area number: '$i'.")

  def fromIntOrOther(i: Int): SeaArea = fromInt(i).getOrElse(Other(i))

  case object Unknown extends SeaArea(0)
  case object Perameri extends SeaArea(1)
  case object Selkameri extends SeaArea(2)
  case object Ahvenanmeri extends SeaArea(3)
  case object Saaristomeri extends SeaArea(4)
  case object Suomenlahti extends SeaArea(5)
  case object Itameri extends SeaArea(6)
  case object Saimaa extends SeaArea(7)
  case object Paijanne extends SeaArea(8)
  case object Kokemaenjoki extends SeaArea(9)
  case object Oulujarvi extends SeaArea(10)
  case object SotkamonJarvet extends SeaArea(11)
  case object KuhmonJarvet extends SeaArea(12)
  case object KuusamonJarvet extends SeaArea(13)
  case object Kiantajarvi extends SeaArea(14)
  case object Simojarvi extends SeaArea(15)
  case object LokkaPorttipahta extends SeaArea(16)
  case object Kemijarvi extends SeaArea(17)
  case object Inarinjarvi extends SeaArea(18)
  case object Nitsijarvi extends SeaArea(19)
  case object Miekkojarvi extends SeaArea(20)
  case object Tornionjoki extends SeaArea(21)
  case object Ahtarinjarvi extends SeaArea(22)
  case object Lappajarvi extends SeaArea(23)
  case object Pyhajarvi extends SeaArea(24)
  case object Lohjanjarvi extends SeaArea(25)

  case class Other(v: Int) extends SeaArea(v)
}

case class FairwayInfo(nameFi: Option[String],
                       nameSe: Option[String],
                       start: Option[String],
                       end: Option[String],
                       depth1: Option[DistanceM],
                       depth2: Option[DistanceM],
                       depth3: Option[DistanceM],
                       lighting: FairwayLighting,
                       classText: String,
                       seaArea: SeaArea,
                       state: Double)
    extends NameLike {
  def depth = depth1 orElse depth2 orElse depth3
}

object FairwayInfo {
  implicit val reader = Reads[FairwayInfo] { json =>
    for {
      fi <- MaritimeJson.nonEmptyOpt(json \ "VAY_NIMISU")
      se <- MaritimeJson.nonEmptyOpt(json \ "VAY_NIMIRU")
      start <- MaritimeJson.nonEmptyOpt(json \ "SELOSTE_AL")
      end <- MaritimeJson.nonEmptyOpt(json \ "SELOSTE_PA")
      depth <- (json \ "KULKUSYV1").validateOpt[DistanceM]
      depth2 <- (json \ "KULKUSYV2").validateOpt[DistanceM]
      depth3 <- (json \ "KULKUSYV3").validateOpt[DistanceM]
      lighting <- (json \ "VALAISTUS").validate[FairwayLighting]
      clazz <- (json \ "VAYLA_LK").validate[String]
      seaArea <- (json \ "MERIAL_NR").validate[SeaArea]
      state <- (json \ "TILA").validate[Double]
    } yield FairwayInfo(fi, se, start, end, depth, depth2, depth3, lighting, clazz, seaArea, state)
  }
}

object MaritimeJson {
  def nonEmptyOpt(lookup: JsLookupResult) = lookup match {
    case JsDefined(json) => nonEmpty.reads(json)
    case _               => JsSuccess(None)
  }

  val nonEmpty = Reads[Option[String]] { json =>
    json.validate[String].map(_.trim).map(s => if (s.nonEmpty) Option(s) else None)
  }

  def doubleReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
    Reads[T] { json =>
      json.validate[Double].map(_.toInt).collect(JsonValidationError(onError(json)))(pf)
    }

  def intReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
    Reads[T] { json =>
      json.validate[Int].collect(JsonValidationError(onError(json)))(pf)
    }
}
