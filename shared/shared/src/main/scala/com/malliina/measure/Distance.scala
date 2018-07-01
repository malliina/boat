package com.malliina.measure

import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Reads, Writes}

/**
  * @param millis millimeters
  */
class Distance(millis: Long) extends Ordered[Distance] {
  private val k = 1000

  override def compare(that: Distance): Int = toMillis compare that.toMillis

  def toMillis = millis

  def toMeters = millis / k

  def toMetersDouble = 1.0D * millis / k

  def toKilometers = toMeters / k

  def toKilometersDouble = toMetersDouble / k

  def +(other: Distance) = (millis + other.toMillis).millimeters

  def -(other: Distance) = millis - other.toMillis

  def ==(other: Distance): Boolean = this.toMillis == other.toMillis

  def !=(other: Distance) = this.toMillis != other.toMillis

  /**
    * @return a string of format 'n units'
    */
  def short =
    if (toKilometers >= 10) s"$toKilometers km"
    else if (toMeters >= 10) s"$toMeters m"
    else s"$toMillis mm"

  override def toString = short
}

object Distance {
  val zero = new Distance(0)

  implicit val json: Format[Distance] = Format[Distance](
    Reads(_.validate[Long].map(_.millimeters)),
    Writes(size => toJson(size.toMillis))
  )
}
