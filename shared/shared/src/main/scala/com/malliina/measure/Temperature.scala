package com.malliina.measure

import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Reads, Writes}

/**
  * @param celsius degrees in Celsius scale
  */
class Temperature(celsius: Double) extends Ordered[Temperature] {
  override def compare(that: Temperature): Int = celsius compare that.toCelsius

  def toCelsius = celsius

  def toFahrenheit = Temperature.celsiusToFahrenheit(celsius)

  def toKelvin = Temperature.celsiusToKelvin(celsius)

  def +(other: Temperature) = (celsius + other.toCelsius).kmh

  def -(other: Temperature) = toCelsius - other.toCelsius

  def ==(other: Temperature) = toCelsius == other.toCelsius

  def !=(other: Temperature) = toCelsius != other.toCelsius

  /**
    * @return a string of format 'n units'
    */
  def formatCelsius = s"$toCelsius C"

  override def toString = formatCelsius
}

object Temperature {
  private val kelvinDiff = 273.15D
  val zeroCelsius = new Temperature(0)
  val absoluteZero = new Temperature(-kelvinDiff)

  implicit val celsiusJson: Format[Temperature] = Format[Temperature](
    Reads(_.validate[Double].map(_.celsius)),
    Writes(size => toJson(size.toCelsius))
  )

  val fahrenheitJson: Format[Temperature] = Format[Temperature](
    Reads(_.validate[Double].map(_.fahrenheit)),
    Writes(size => toJson(size.toFahrenheit))
  )

  def celsiusToFahrenheit(c: Double): Double = c * 9 / 5 + 32

  def fahrenheitToCelsius(f: Double): Double = (f - 32) * 5 / 9

  def kelvinToCelsius(k: Double): Double = k - kelvinDiff

  def celsiusToKelvin(c: Double): Double = c + kelvinDiff
}
