package com.malliina.boat.parsing

import com.malliina.boat.{RawSentence, SingleError}
import com.malliina.measure.Inputs.{toDouble, toInt}
import com.malliina.measure.{
  DistanceDoubleM,
  LatitudeDM,
  LongitudeDM,
  SpeedDoubleM,
  TemperatureDouble
}

object SentenceParser extends NMEA0183Parser {
  val dpt = """\$(\w{2})DPT,([\d\.]+),([\d\.]+),.*""".r
  val vtg =
    """\$(\w{2})VTG,([\d\.]+),T,([\d\.]+),M,([\d\.]+),N,([\d\.]+),K.*""".r
  val mtw = """\$(\w{2})MTW,([\d\.]+),C\*.*""".r
  //  $GPZDA,150042.000,17,08,2019,,*50
  val zda = """\$(\w{2})ZDA,([\d-\.]+),([\d-]+),(\d+),(\d+),([\d-]*),(\d*)\*.*""".r
  val gga =
    """\$(\w{2})GGA,([\d-\.]+),([\d\.]+,[NS]),([\d\.]+,[EW]),(\d+),(\d+),([\d\.]+),([\d-\.]+),M,([\d\.]*),M,([\d\.]*),.*""".r
  val gsa = """\$(\w{2})GSA,(\w+),(\d+),.*""".r
  val rmc =
    """\$(\w{2})RMC,([\d-\.]+),\w+,([\d\.]+,[NS]),([\d\.]+,[EW]),([\d\.]+),([\d\.]+),([\d]+),.*""".r
  val gsv = """\$(\w{2})GSV,\d+,\d+,([\d\.]+),\d+,([\d\.]+),([\d\.]+),.*""".r

  def parse(raw: RawSentence): Either[SentenceError, TalkedSentence] = {
    def asInt(s: String) = toInt(s)
    def optInt(s: String) = if (s.nonEmpty) toInt(s).map(Option.apply) else Right(None)
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
            offHours <- optInt(offsetHours)
            offMinutes <- optInt(offsetMinutes)
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
      case gsa(talker, mode, fix) =>
        mapFailures {
          for {
            m <- GPSMode(mode)
            f <- GPSFix(fix)
          } yield GSAMessage(talker, m, f)
        }
      case rmc(talker, utc, latDM, lngDM, knots, course, date) =>
        mapFailures {
          for {
            time <- ZDAMessage.parseTimeUtc(utc)
            lat <- LatitudeDM.parse(latDM)
            lng <- LongitudeDM.parse(lngDM)
            kn <- toDouble(knots)
            c <- toDouble(course)
            d <- RMCMessage.parseDate(date)
          } yield RMCMessage(talker, time, d, kn.knots, c)
        }
      case gsv(talker, satellites, elevation, azimuth) =>
        mapFailures {
          for {
            s <- toInt(satellites)
            e <- Elevation(elevation)
            a <- Azimuth(azimuth)
          } yield GSVMessage(talker, s, e, a)
        }
      case _ =>
        Left(UnknownSentence(raw, s"Unknown sentence: '$raw'."))
    }
  }
}
