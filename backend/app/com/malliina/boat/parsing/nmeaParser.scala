package com.malliina.boat.parsing

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}

import com.malliina.boat.{RawSentence, SingleError}
import com.malliina.measure.Inputs.{toDouble, toInt}
import com.malliina.measure.{DistanceDoubleM, DistanceM, LatitudeDM, LongitudeDM, SpeedDoubleM, SpeedM, Temperature, TemperatureDouble}

sealed trait TalkedSentence {
  def talker: String
}

case class VTGMessage(talker: String,
                      courseTrue: Double,
                      courseMagnetic: Double,
                      speedKnots: SpeedM,
                      speedKmh: SpeedM)
    extends TalkedSentence
case class GPTMessage(talker: String, depth: DistanceM, offset: DistanceM) extends TalkedSentence
case class MTWMessage(talker: String, temperature: Temperature) extends TalkedSentence
case class ZDAMessage(talker: String,
                      timeUtc: LocalTime,
                      day: Int,
                      month: Int,
                      year: Int,
                      timeZoneOffsetHours: Int,
                      timeZoneOffsetMinutes: Int)
    extends TalkedSentence {
  val date = LocalDate.of(year, month, day)
  val dateTimeUtc = OffsetDateTime.of(date, timeUtc, ZoneOffset.UTC)
  def time = timeUtc
  def isSuspect = time.getHour == 0 && time.getMinute == 0 && (time.getSecond >= 0 && time.getSecond <= 15)
  // It seems like the time zone sign is reported incorrectly in my plotter
  // (actual +03 reported -03), so we read all times as UTC
//  val localZone = ZoneOffset.ofHoursMinutes(timeZoneOffsetHours, timeZoneOffsetMinutes)
//  val dateTime = dateTimeUtc.withOffsetSameInstant(localZone)
}

object ZDAMessage {
  val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")

  def parseTimeUtc(s: String): Either[SingleError, LocalTime] =
    try { Right(LocalTime.parse(s, timeFormatter)) } catch {
      case _: DateTimeParseException =>
        Left(SingleError.input(s"Invalid time: '$s'."))
    }
}

case class GGAMessage(talker: String,
                      timeUtc: LocalTime,
                      lat: LatitudeDM,
                      lng: LongitudeDM,
                      gpsQuality: Int,
                      svCount: Int,
                      hdop: Double,
                      orthometricHeight: DistanceM,
                      geoidSeparation: Double,
                      diffAge: Double)
    extends TalkedSentence

object TalkedSentence {
  val dpt = """\$(\w{2})DPT,([\d\.]+),([\d\.]+),.*""".r
  val vtg =
    """\$(\w{2})VTG,([\d\.]+),T,([\d\.]+),M,([\d\.]+),N,([\d\.]+),K.*""".r
  val mtw = """\$(\w{2})MTW,([\d\.]+),C\*.*""".r
  val zda = """\$(\w{2})ZDA,([\d-]+),([\d-]+),(\d+),(\d+),([\d-]+),(\d+)\*.*""".r
  val gga =
    """\$(\w{2})GGA,([\d-]+),([\d\.]+,[NS]),([\d\.]+,[EW]),(\d+),(\d+),([\d\.]+),([\d\.]+),M,([\d\.]*),M,([\d\.]*),.*""".r

  def parse(raw: RawSentence): Either[SingleError, TalkedSentence] = raw.sentence match {
    case dpt(talker, depth, offset) =>
      for {
        d <- toDouble(depth)
        o <- toDouble(offset)
      } yield GPTMessage(talker, d.meters, o.meters)
    case vtg(talker, courseTrue, courseMagnetic, speedKnots, speedKmh) =>
      for {
        trueCourse <- toDouble(courseTrue)
        magneticCourse <- toDouble(courseMagnetic)
        knots <- toDouble(speedKnots)
        kmh <- toDouble(speedKmh)
      } yield VTGMessage(talker, trueCourse, magneticCourse, knots.knots, kmh.kmh)
    case mtw(talker, temp) =>
      toDouble(temp).map { t =>
        MTWMessage(talker, t.celsius)
      }
    case zda(talker, utc, day, month, year, offsetHours, offsetMinutes) =>
      for {
        time <- ZDAMessage.parseTimeUtc(utc)
        d <- toInt(day)
        m <- toInt(month)
        y <- toInt(year)
        offHours <- toInt(offsetHours)
        offMinutes <- toInt(offsetMinutes)
      } yield ZDAMessage(talker, time, d, m, y, offHours, offMinutes)
    case gga(talker, utc, latDM, lngDM, quality, svCount, hdopStr, height, geoid, diff) =>
      for {
        time <- ZDAMessage.parseTimeUtc(utc)
        lat <- LatitudeDM.parse(latDM)
        lng <- LongitudeDM.parse(lngDM)
        q <- toInt(quality)
        svs <- toInt(svCount)
        hdop <- toDouble(hdopStr)
        h <- toDouble(height).map(_.meters)
        geo <- toDouble(geoid)
        d <- toDouble(diff)
      } yield GGAMessage(talker, time, lat, lng, q, svs, hdop, h, geo, d)
    case _ =>
      Left(SingleError.input(s"Unknown sentence: '$raw'."))
  }

}
