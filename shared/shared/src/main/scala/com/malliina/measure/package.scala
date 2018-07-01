package com.malliina

package object measure {
  private val k = 1000L

  /**
    * @param amount integer amount of some distance unit
    */
  implicit final class DistanceInt(val amount: Int) extends AnyVal with DistanceConversions {
    protected def asMillis(multiplier: Long): Long = multiplier * amount
  }

  implicit final class DistanceLong(val amount: Long) extends AnyVal with DistanceConversions {
    protected def asMillis(multiplier: Long): Long = multiplier * amount
  }

  implicit final class DistanceDouble(val amount: Double) extends AnyVal with DistanceConversions {
    protected def asMillis(multiplier: Long): Long = (multiplier * amount).toLong
  }

  trait DistanceConversions extends Any {
    protected def asDistance(multiplier: Long): Distance = new Distance(asMillis(multiplier))

    protected def asMillis(multiplier: Long): Long

    def mm = asDistance(1)

    def millimeters = asDistance(1)

    def m = asDistance(k)

    def meters = asDistance(k)

    def km = asDistance(k * k)

    def kilometers = asDistance(k * k)
  }

  implicit final class SpeedInt(private val amount: Int) extends AnyVal with SpeedConversions {
    protected def asKmh(multiplier: Double): Double = multiplier * amount
  }

  implicit final class SpeedLong(private val amount: Long) extends AnyVal with SpeedConversions {
    protected def asKmh(multiplier: Double): Double = multiplier * amount
  }

  implicit final class SpeedDouble(private val amount: Double) extends AnyVal with SpeedConversions {
    protected def asKmh(multiplier: Double): Double = (multiplier * amount).toLong
  }

  trait SpeedConversions extends Any {
    protected def asSpeed(multiplier: Double): Speed = new Speed(asKmh(multiplier))

    protected def asKmh(multiplier: Double): Double

    def `m/s` = metersPerSecond

    def metersPerSecond = asSpeed(3.6)

    def kmh = asSpeed(1)

    def kn = knots

    def knots = asSpeed(1.852)
  }

  implicit final class TemperatureInt(private val amount: Int) extends AnyVal with TemperatureConversions {
    def celsius = asCelsius(amount)

    def fahrenheit = fromFahrenheit(amount)

    def kelvin = fromKelvin(amount)
  }

  implicit final class TemperatureLong(private val amount: Long) extends AnyVal with TemperatureConversions {
    def celsius = asCelsius(amount)

    def fahrenheit = fromFahrenheit(amount)

    def kelvin = fromKelvin(amount)
  }

  implicit final class TemperatureDouble(private val amount: Double) extends AnyVal with TemperatureConversions {
    def celsius = asCelsius(amount)

    def fahrenheit = fromFahrenheit(amount)

    def kelvin = fromKelvin(amount)
  }

  trait TemperatureConversions extends Any {
    protected def asCelsius(celsius: Double): Temperature = new Temperature(celsius)

    protected def fromFahrenheit(f: Double) = asCelsius(Temperature.fahrenheitToCelsius(f))

    protected def fromKelvin(k: Double) = asCelsius(Temperature.kelvinToCelsius(k))
  }

}
