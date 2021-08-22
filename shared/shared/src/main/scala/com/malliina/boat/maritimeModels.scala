package com.malliina.boat

import com.malliina.boat.MaritimeJson.partialReader
import com.malliina.measure.DistanceM

import io.circe._
import io.circe.generic.semiauto._

trait NameLike {
  def nameFi: Option[String]
  def nameSe: Option[String]

  def name(lang: Lang): Option[String] =
    if (lang == Lang.se) nameSe.orElse(nameFi) else nameFi.orElse(nameSe)
}

sealed trait FairwayLighting

object FairwayLighting {
  implicit val reader: Decoder[FairwayLighting] =
    partialReader[Double, FairwayLighting](d => s"Unknown fairway lighting: '$d'.") {
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
  implicit val reader: Decoder[FairwaySeaType] =
    partialReader[Double, FairwaySeaType](d => s"Unknown fairway sea type: '$d'.") {
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
  implicit val reader: Decoder[SeaArea] = Decoder.decodeDouble.map { d => fromIntOrOther(d.toInt) }
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

case class FairwayInfo(
  nameFi: Option[String],
  nameSe: Option[String],
  start: Option[String],
  end: Option[String],
  depth: Option[DistanceM],
  depth2: Option[DistanceM],
  depth3: Option[DistanceM],
  lighting: FairwayLighting,
  classText: String,
  seaArea: SeaArea,
  state: Double
) extends NameLike {
  def bestDepth = depth orElse depth2 orElse depth3
}

object FairwayInfo {
  import MaritimeJson.nonEmptyOpt
  implicit val decoder: Decoder[FairwayInfo] = new Decoder[FairwayInfo] {
    final def apply(c: HCursor): Decoder.Result[FairwayInfo] =
      for {
        fi <- c.downField("VAY_NIMISU").as[Option[String]](nonEmptyOpt)
        se <- c.downField("VAY_NIMIRU").as[Option[String]](nonEmptyOpt)
        start <- c.downField("SELOSTE_AL").as[Option[String]](nonEmptyOpt)
        end <- c.downField("SELOSTE_PA").as[Option[String]](nonEmptyOpt)
        depth <- c.downField("KULKUSYV1").as[Option[DistanceM]]
        depth2 <- c.downField("KULKUSYV2").as[Option[DistanceM]]
        depth3 <- c.downField("KULKUSYV3").as[Option[DistanceM]]
        lighting <- c.downField("VALAISTUS").as[FairwayLighting]
        clazz <- c.downField("VAYLA_LK").as[String]
        seaArea <- c.downField("MERIAL_NR").as[SeaArea]
        state <- c.downField("TILA").as[Double]
      } yield FairwayInfo(
        fi,
        se,
        start,
        end,
        depth,
        depth2,
        depth3,
        lighting,
        clazz,
        seaArea,
        state
      )
  }
}

object MaritimeJson {
  val nonEmpty: Decoder[String] = Decoder.decodeString.emap { str =>
    val trimmed = str.trim
    if (trimmed.nonEmpty) Right(trimmed)
    else Left("Empty string. Non-empty required.")
  }
  val nonEmptyOpt: Decoder[Option[String]] = Decoder.decodeOption(nonEmpty)
//  val nonEmpty = Reads[Option[String]] { json =>
//    json.validate[String].map(_.trim).map(s => if (s.nonEmpty) Option(s) else None)
//  }

//  def nonEmptyOpt(lookup: JsLookupResult): JsResult[Option[String]] = lookup match {
//    case JsDefined(json) => nonEmpty.reads(json)
//    case _               => JsSuccess(None)
//  }

//  def doubleReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
//    Reads[T] { json =>
//      json.validate[Double].map(_.toInt).collect(JsonValidationError(onError(json)))(pf)
//    }

//  def intReader[T](onError: JsValue => String)(pf: PartialFunction[Int, T]): Reads[T] =
//    partialReader[Int, T](onError)(pf)
//
//  def stringReader[T](onError: JsValue => String)(pf: PartialFunction[String, T]): Reads[T] =
//    partialReader[String, T](onError)(pf)

  def partialReader[In: Decoder, Out](
    onError: In => String
  )(pf: PartialFunction[In, Out]): Decoder[Out] = {
    Decoder[In].emap { in => pf.lift(in).toRight(onError(in)) }
  }
}
