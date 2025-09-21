package com.malliina.boat.parsing

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import com.malliina.http.SingleError
import com.malliina.measure.{DistanceM, Inputs, LatitudeDM, LongitudeDM, SpeedM, Temperature}

sealed trait TalkedSentence:
  def talker: String

case class VTGMessage(
  talker: String,
  courseTrue: Double,
  courseMagnetic: Double,
  speedKnots: SpeedM,
  speedKmh: SpeedM
) extends TalkedSentence
case class DPTMessage(talker: String, depth: DistanceM, offset: DistanceM) extends TalkedSentence
case class MTWMessage(talker: String, temperature: Temperature) extends TalkedSentence
case class ZDAMessage(
  talker: String,
  timeUtc: LocalTime,
  day: Int,
  month: Int,
  year: Int,
  timeZoneOffsetHours: Option[Int],
  timeZoneOffsetMinutes: Option[Int]
) extends TalkedSentence:
  val date = LocalDate.of(year, month, day)
  val dateTimeUtc = OffsetDateTime.of(date, timeUtc, ZoneOffset.UTC)
  def time: LocalTime = timeUtc
  def isSuspect: Boolean =
    time.getHour == 0 && time.getMinute == 0 && (time.getSecond >= 0 && time.getSecond <= 15)
  // It seems like the time zone sign is reported incorrectly in my plotter
  // (actual +03 reported -03), so we read all times as UTC
//  val localZone = ZoneOffset.ofHoursMinutes(timeZoneOffsetHours, timeZoneOffsetMinutes)
//  val dateTime = dateTimeUtc.withOffsetSameInstant(localZone)

object ZDAMessage:
  private val timeFormatterSimrad = DateTimeFormatter.ofPattern("HHmmss")
  private val timeFormatterGps = DateTimeFormatter.ofPattern("HHmmss.SSS")

  def parseTimeUtc(s: String): Either[SingleError, LocalTime] =
    parseFormattedTime(s, timeFormatterSimrad)
      .orElse(parseFormattedTime(s, timeFormatterGps))

  private def parseFormattedTime(
    s: String,
    formatter: DateTimeFormatter
  ): Either[SingleError, LocalTime] =
    try Right(LocalTime.parse(s, formatter))
    catch
      case _: DateTimeParseException =>
        Left(SingleError.input(s"Invalid time: '$s', expected format '$formatter'."))

case class GGAMessage(
  talker: String,
  timeUtc: LocalTime,
  lat: LatitudeDM,
  lng: LongitudeDM,
  gpsQuality: Int,
  svCount: Int,
  hdop: Double,
  orthometricHeight: DistanceM,
  geoidSeparation: Option[Double],
  diffAge: Option[Double]
) extends TalkedSentence

trait PrimitiveParsing:
  def attempt[In, Out](in: In, onFail: In => String)(pf: PartialFunction[In, Out]) =
    pf.lift(in).toRight(SingleError.input(onFail(in)))

  def limitedInt[T](s: String, p: Int => Boolean, build: Int => T): Either[SingleError, T] =
    Inputs
      .toInt(s)
      .flatMap: i =>
        if p(i) then Right(build(i))
        else Left(SingleError.input(s"Invalid input: '$s'."))

sealed trait GPSMode
object GPSMode extends PrimitiveParsing:
  case object Automatic extends GPSMode
  case object Manual extends GPSMode

  def apply(s: String): Either[SingleError, GPSMode] =
    attempt[String, GPSMode](s, in => s"Invalid GPS mode: '$in'."):
      case "A" => Automatic
      case "M" => Manual

sealed abstract class GPSFix(val value: String)
object GPSFix extends PrimitiveParsing:
  case object NoFix extends GPSFix("1")
  case object Fix2D extends GPSFix("2")
  case object Fix3D extends GPSFix("3")
  case class OtherFix(s: String) extends GPSFix(s)

  val all = Seq(Fix2D, Fix3D, NoFix)

  def orOther(s: String) = apply(s).getOrElse(OtherFix(s))

  def apply(s: String): Either[SingleError, GPSFix] =
    all.find(_.value == s).toRight(SingleError.input(s"Invalid GPS fix: '$s'."))

case class GSAMessage(talker: String, mode: GPSMode, fix: GPSFix) extends TalkedSentence

case class RMCMessage(
  talker: String,
  timeUtc: LocalTime,
  date: LocalDate,
  speed: SpeedM,
  course: Double
) extends TalkedSentence:
  val dateTimeUtc = OffsetDateTime.of(date, timeUtc, ZoneOffset.UTC)

object RMCMessage:
  val dateFormatter = DateTimeFormatter.ofPattern("ddMMyy")

  def parseDate(s: String) =
    try Right(LocalDate.parse(s, dateFormatter))
    catch
      case _: DateTimeParseException =>
        Left(SingleError.input(s"Invalid date: '$s', expected format '$dateFormatter'."))

opaque type Elevation = Int

object Elevation extends PrimitiveParsing:
  def apply(s: String): Either[SingleError, Elevation] =
    limitedInt(s, i => i >= 0 && i <= 90, (i: Int) => i)

opaque type Azimuth = Int

object Azimuth extends PrimitiveParsing:
  def apply(s: String): Either[SingleError, Azimuth] =
    limitedInt(s, i => i >= 0 && i <= 360, (i: Int) => i)

case class GSVMessage(talker: String, satellites: Int, elevation: Elevation, azimuth: Azimuth)
  extends TalkedSentence
