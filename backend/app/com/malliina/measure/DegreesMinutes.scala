package com.malliina.measure

import com.malliina.boat.{Latitude, Longitude, SingleError}
import com.malliina.measure.Inputs._

sealed trait HemisphereDirection

object HemisphereDirection {
  def parse(in: String): Either[SingleError, HemisphereDirection] = in match {
    case "N" => Right(North)
    case "S" => Right(South)
    case "E" => Right(East)
    case "W" => Right(West)
    case _   => Left(SingleError.input(s"Invalid direction: '$in'."))
  }
}

sealed trait NorthOrSouth extends HemisphereDirection

object NorthOrSouth {
  def apply(in: String): Either[SingleError, NorthOrSouth] = in match {
    case "N" => Right(North)
    case "S" => Right(South)
    case _   => Left(SingleError.input(s"Invalid direction, must be 'N' or 'S': '$in'."))
  }
}

case object North extends NorthOrSouth
case object South extends NorthOrSouth

sealed trait EastOrWest extends HemisphereDirection

object EastOrWest {
  def apply(in: String): Either[SingleError, EastOrWest] = in match {
    case "E" => Right(East)
    case "W" => Right(West)
    case _   => Left(SingleError.input(s"Invalid direction, must be 'W' or 'E': '$in'."))
  }
}

case object East extends EastOrWest
case object West extends EastOrWest

/** Latitude degrees minutes.
  */
case class LatitudeDM(degrees: Int, minutes: Double, direction: NorthOrSouth) {
  def toDecimalDegrees = LatitudeDM.toDegreesDecimal(this)
}

object LatitudeDM {
  val latitude = """(\d{4}\.\d+),([NS])""".r

  def parse(in: String) = in match {
    case latitude(ddStr, nsStr) =>
      for {
        dd <- DegreesMinutes.parse(ddStr)
        ns <- NorthOrSouth(nsStr)
      } yield dd.latitude(ns)
    case _ =>
      Left(SingleError.input(s"Invalid latitude: '$in'."))
  }

  def toDegreesDecimal(dm: LatitudeDM): Latitude = {
    val multiplier = dm.direction match {
      case North => 1.0
      case South => -1.0
    }
    Latitude(multiplier * (dm.degrees + dm.minutes / 60))
  }
}

case class LongitudeDM(degrees: Int, minutes: Double, direction: EastOrWest) {
  def toDecimalDegrees: Longitude = LongitudeDM.toDegreesDecimal(this)
}

object LongitudeDM {
  val longitude = """(\d{5}\.\d+),([WE])""".r

  def parse(in: String) = in match {
    case longitude(ddStr, ewStr) =>
      for {
        dd <- DegreesMinutes.parse(ddStr)
        ew <- EastOrWest(ewStr)
      } yield dd.longitude(ew)
    case _ =>
      Left(SingleError.input(s"Invalid longitude: '$in'."))
  }

  def toDegreesDecimal(dm: LongitudeDM): Longitude = {
    val multiplier = dm.direction match {
      case East => 1.0
      case West => -1.0
    }
    Longitude(multiplier * (dm.degrees + dm.minutes / 60))
  }
}

case class DegreesMinutes(degrees: Int, minutes: Double) {
  def latitude(ns: NorthOrSouth) = LatitudeDM(degrees, minutes, ns)
  def longitude(ew: EastOrWest) = LongitudeDM(degrees, minutes, ew)
}

object DegreesMinutes {
  val dm = """(\d{2,3})(\d{2}\.\d+)""".r

  def parse(in: String): Either[SingleError, DegreesMinutes] = in match {
    case dm(degrees, minutes) =>
      for {
        degs <- toInt(degrees)
        mins <- toDouble(minutes)
      } yield DegreesMinutes(degs, mins)
    case _ =>
      Left(SingleError.input(s"Invalid degrees minutes: '$in'."))
  }
}
