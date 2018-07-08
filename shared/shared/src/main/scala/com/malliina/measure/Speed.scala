package com.malliina.measure

import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Reads, Writes}

/**
  * @param kmh kilometers per hour
  */
class Speed(kmh: Double) extends Ordered[Speed] {
  val knotInKmh = 1.852D
  val meterPerSecondInKmh = 3.6D

  override def compare(that: Speed): Int = kmh compare that.toKmh

  def toKmh = kmh

  def toKmhDouble = kmh

  def toKnots = kmh / knotInKmh

  def toKnotsDouble = kmh / knotInKmh

  def toMetersPerSecond = kmh / meterPerSecondInKmh

  def +(other: Speed) = (kmh + other.toKmh).kmh

  def -(other: Speed) = toKmh - other.toKmh

  def ==(other: Speed) = this.toKmh == other.toKmh

  def !=(other: Speed) = this.toKmh != other.toKmh

  /**
    * @return a string of format 'n units'
    */
  def formatKmh = s"$toKmh kmh"

  def formatKnots = s"$toKnots kn"

  override def toString = formatKmh
}

object Speed {
  val zero = new Speed(0)

  def kmh(d: Double): Speed = new Speed(d)

  val kmhJson: Format[Speed] = Format[Speed](
    Reads(_.validate[Double].map(_.kmh)),
    Writes(size => toJson(size.toKmh))
  )

  implicit val knotsJson: Format[Speed] = Format[Speed](
    Reads(_.validate[Double].map(_.knots)),
    Writes(size => toJson(size.toKnots))
  )
}
