package com.malliina.boat.parsing

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}

import com.malliina.boat.{RawSentence, SingleError}
import com.malliina.measure.Inputs.{toDouble, toInt}
import com.malliina.measure.{
  DistanceDoubleM,
  DistanceM,
  LatitudeDM,
  LongitudeDM,
  SpeedDoubleM,
  SpeedM,
  Temperature,
  TemperatureDouble
}

sealed trait TalkedSentence {
  def talker: String
}

case class VTGMessage(talker: String,
                      courseTrue: Double,
                      courseMagnetic: Double,
                      speedKnots: SpeedM,
                      speedKmh: SpeedM)
    extends TalkedSentence
case class DPTMessage(talker: String, depth: DistanceM, offset: DistanceM) extends TalkedSentence
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
  def time: LocalTime = timeUtc
  def isSuspect: Boolean =
    time.getHour == 0 && time.getMinute == 0 && (time.getSecond >= 0 && time.getSecond <= 15)
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
                      geoidSeparation: Option[Double],
                      diffAge: Option[Double])
    extends TalkedSentence

object TalkedSentence extends NMEA0183Parser {
  val dpt = """\$(\w{2})DPT,([\d\.]+),([\d\.]+),.*""".r
  val vtg =
    """\$(\w{2})VTG,([\d\.]+),T,([\d\.]+),M,([\d\.]+),N,([\d\.]+),K.*""".r
  val mtw = """\$(\w{2})MTW,([\d\.]+),C\*.*""".r
  val zda = """\$(\w{2})ZDA,([\d-]+),([\d-]+),(\d+),(\d+),([\d-]+),(\d+)\*.*""".r
  val gga =
    """\$(\w{2})GGA,([\d-]+),([\d\.]+,[NS]),([\d\.]+,[EW]),(\d+),(\d+),([\d\.]+),([\d-\.]+),M,([\d\.]*),M,([\d\.]*),.*""".r

  def parse(raw: RawSentence): Either[SentenceError, TalkedSentence] = {
    def asInt(s: String) = toInt(s)
    def asDouble(s: String) = toDouble(s)
    def mapFailures[T](e: Either[SingleError, T]) =
      e.left.map(err => InvalidSentence(raw, err.message))

    raw.sentence match {
      case dpt(talker, depth, offset) =>
        mapFailures {
          for {
            d <- asDouble(depth)
            o <- asDouble(offset)
          } yield DPTMessage(talker, d.meters, o.meters)
        }
      case vtg(talker, courseTrue, courseMagnetic, speedKnots, speedKmh) =>
        mapFailures {
          for {
            trueCourse <- asDouble(courseTrue)
            magneticCourse <- asDouble(courseMagnetic)
            knots <- asDouble(speedKnots)
            kmh <- asDouble(speedKmh)
          } yield VTGMessage(talker, trueCourse, magneticCourse, knots.knots, kmh.kmh)
        }
      case mtw(talker, temp) =>
        mapFailures {
          asDouble(temp).map { t =>
            MTWMessage(talker, t.celsius)
          }
        }
      case zda(talker, utc, day, month, year, offsetHours, offsetMinutes) =>
        mapFailures {
          for {
            time <- ZDAMessage.parseTimeUtc(utc)
            d <- asInt(day)
            m <- asInt(month)
            y <- asInt(year)
            offHours <- asInt(offsetHours)
            offMinutes <- asInt(offsetMinutes)
          } yield ZDAMessage(talker, time, d, m, y, offHours, offMinutes)
        }
      case gga(talker, utc, latDM, lngDM, quality, svCount, hdopStr, height, geoid, diff) =>
        mapFailures {
          for {
            time <- ZDAMessage.parseTimeUtc(utc)
            lat <- LatitudeDM.parse(latDM)
            lng <- LongitudeDM.parse(lngDM)
            q <- asInt(quality)
            svs <- asInt(svCount)
            hdop <- asDouble(hdopStr)
            h <- asDouble(height).map(_.meters)
            geo <- if (geoid.isEmpty) Right(None) else asDouble(geoid).map(Option.apply)
            d <- if (diff.isEmpty) Right(None) else asDouble(diff).map(Option.apply)
          } yield GGAMessage(talker, time, lat, lng, q, svs, hdop, h, geo, d)
        }
      case _ =>
        Left(UnknownSentence(raw, s"Unknown sentence: '$raw'."))
    }
  }

}
