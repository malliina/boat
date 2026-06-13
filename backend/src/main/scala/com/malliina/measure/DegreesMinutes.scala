package com.malliina.measure

import com.malliina.http.SingleError
import com.malliina.measure.Inputs.*
import HemisphereDirection.readString
import com.malliina.geo.{Latitude, Longitude}

sealed trait HemisphereDirection

object HemisphereDirection:
  def parse(in: String): Either[SingleError, HemisphereDirection] =
    readString(in, s"Invalid direction: '$in'."):
      case "N" => NorthOrSouth.North
      case "S" => NorthOrSouth.South
      case "E" => EastOrWest.East
      case "W" => EastOrWest.West

  def readString[T](in: String, onFail: => String)(
    pf: PartialFunction[String, T]
  ): Either[SingleError, T] =
    pf.lift(in).map(Right.apply).getOrElse(Left(SingleError.input(onFail)))

enum NorthOrSouth extends HemisphereDirection:
  case North
  case South

object NorthOrSouth:
  def apply(in: String): Either[SingleError, NorthOrSouth] =
    readString(in, s"Invalid direction, must be 'N' or 'S': '$in'."):
      case "N" => North
      case "S" => South

enum EastOrWest extends HemisphereDirection:
  case East
  case West

object EastOrWest:
  def apply(in: String): Either[SingleError, EastOrWest] =
    readString(in, s"Invalid direction, must be 'W' or 'E': '$in'."):
      case "E" => East
      case "W" => West

/** Latitude degrees minutes.
  */
case class LatitudeDM(degrees: Int, minutes: Double, direction: NorthOrSouth):
  def toDecimalDegrees = LatitudeDM.toDegreesDecimal(this)

object LatitudeDM:
  val latitude = """(\d{4}\.\d+),([NS])""".r

  def parse(in: String): Either[SingleError, LatitudeDM] = in match
    case latitude(ddStr, nsStr) =>
      for
        dd <- DegreesMinutes.parse(ddStr)
        ns <- NorthOrSouth(nsStr)
      yield dd.latitude(ns)
    case _ =>
      Left(SingleError.input(s"Invalid latitude: '$in'."))

  def toDegreesDecimal(dm: LatitudeDM): Latitude =
    val multiplier = dm.direction match
      case NorthOrSouth.North => 1.0
      case NorthOrSouth.South => -1.0
    Latitude.unsafe(multiplier * (dm.degrees + dm.minutes / 60))

case class LongitudeDM(degrees: Int, minutes: Double, direction: EastOrWest):
  def toDecimalDegrees: Longitude = LongitudeDM.toDegreesDecimal(this)

object LongitudeDM:
  val longitude = """(\d{5}\.\d+),([WE])""".r

  def parse(in: String): Either[SingleError, LongitudeDM] = in match
    case longitude(ddStr, ewStr) =>
      for
        dd <- DegreesMinutes.parse(ddStr)
        ew <- EastOrWest(ewStr)
      yield dd.longitude(ew)
    case _ =>
      Left(SingleError.input(s"Invalid longitude: '$in'."))

  def toDegreesDecimal(dm: LongitudeDM): Longitude =
    val multiplier = dm.direction match
      case EastOrWest.East => 1.0
      case EastOrWest.West => -1.0
    Longitude.unsafe(multiplier * (dm.degrees + dm.minutes / 60))

case class DegreesMinutes(degrees: Int, minutes: Double):
  def latitude(ns: NorthOrSouth) = LatitudeDM(degrees, minutes, ns)
  def longitude(ew: EastOrWest) = LongitudeDM(degrees, minutes, ew)

object DegreesMinutes:
  private val dm = """(\d{2,3})(\d{2}\.\d+)""".r

  def parse(in: String): Either[SingleError, DegreesMinutes] = in match
    case dm(degrees, minutes) =>
      for
        degs <- toInt(degrees)
        mins <- toDouble(minutes)
      yield DegreesMinutes(degs, mins)
    case _ =>
      Left(SingleError.input(s"Invalid degrees minutes: '$in'."))
