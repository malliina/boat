package com.malliina

package object measure {
  private val k = 1000L

  /**
    * @param amount integer amount of some distance unit
    */
  implicit final class DistanceInt(val amount: Int) extends DistanceConversions {
    protected def asMillis(multiplier: Long): Long = multiplier * amount
  }

  implicit final class DistanceLong(val amount: Long) extends DistanceConversions {
    protected def asMillis(multiplier: Long): Long = multiplier * amount
  }

  implicit final class DistanceDouble(val amount: Double) extends DistanceConversions {
    protected def asMillis(multiplier: Long): Long = (multiplier * amount).toLong
  }

  trait DistanceConversions {
    protected def asDistance(multiplier: Long): Distance = new Distance(asMillis(multiplier))

    protected def asMillis(multiplier: Long): Long

    def mm = asDistance(1)

    def millimeters = asDistance(1)

    def m = asDistance(k)

    def meters = asDistance(k)

    def km = asDistance(k * k)

    def kilometers = asDistance(k * k)
  }

}
