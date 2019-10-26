package com.malliina.boat.db

import java.util.concurrent.TimeUnit

import com.malliina.measure.{DistanceDoubleM, DistanceM, SpeedDoubleM, SpeedM, Temperature, TemperatureDouble}
import com.malliina.values.Username
import io.getquill.NamingStrategy
import io.getquill.context.Context
import io.getquill.idiom.Idiom

import scala.concurrent.duration.FiniteDuration

trait StatsQuotes[I <: Idiom, N <: NamingStrategy] { this: Context[I, N] with Quotes[I, N] =>
  def mappedNumeric[A, B](f: B => A, g: A => B)(implicit na: Numeric[A]): Numeric[B] =
    new Numeric[B] {
      override def plus(x: B, y: B): B = g(na.plus(f(x), f(y)))
      override def minus(x: B, y: B): B = g(na.minus(f(x), f(y)))
      override def times(x: B, y: B): B = g(na.times(f(x), f(y)))
      override def negate(x: B): B = g(na.negate(f(x)))
      override def fromInt(x: Int): B = g(na.fromInt(x))
      override def parseString(str: String): Option[B] = na.parseString(str).map(g)
      override def toInt(x: B): Int = na.toInt(f(x))
      override def toLong(x: B): Long = na.toLong(f(x))
      override def toFloat(x: B): Float = na.toFloat(f(x))
      override def toDouble(x: B): Double = na.toDouble(f(x))
      override def compare(x: B, y: B): Int = na.compare(f(x), f(y))
    }
  implicit val numericDuration: Numeric[FiniteDuration] = new Numeric[FiniteDuration] {
    override def plus(x: FiniteDuration, y: FiniteDuration): FiniteDuration = x.plus(y)
    override def minus(x: FiniteDuration, y: FiniteDuration): FiniteDuration = x.minus(y)
    override def times(x: FiniteDuration, y: FiniteDuration): FiniteDuration = x * y
    override def negate(x: FiniteDuration): FiniteDuration = -x
    override def fromInt(x: Int): FiniteDuration =
      FiniteDuration(Numeric.LongIsIntegral.fromInt(x), TimeUnit.NANOSECONDS)
    override def parseString(str: String): Option[FiniteDuration] = None
    override def toInt(x: FiniteDuration): Int = toDouble(x).toInt
    override def toLong(x: FiniteDuration): Long = x.toNanos
    override def toFloat(x: FiniteDuration): Float = toDouble(x).toFloat
    override def toDouble(x: FiniteDuration): Double = x.toUnit(TimeUnit.NANOSECONDS)
    override def compare(x: FiniteDuration, y: FiniteDuration): Int = x.compare(y)
  }
  // TODO move to companion objects
  implicit val numDistance: Numeric[DistanceM] =
    mappedNumeric[Double, DistanceM](_.meters, _.meters)
  implicit val numSpeed: Numeric[SpeedM] = mappedNumeric[Double, SpeedM](_.mps, _.mps)
  implicit val numTemperature: Numeric[Temperature] =
    mappedNumeric[Double, Temperature](_.celsius, _.celsius)

  /** For daily stats, groups by date. For monthly stats, groups by year and month. For yearly
    * stats, groups by year alone.
    */
  val dailyQuery = quote { user: Username =>
    tracksBy(user).groupBy(_.startDate).map {
      case (date, ts) =>
        DailyAggregates(
          date,
          ts.map(_.distance).sum,
          ts.map(_.duration).sum,
          ts.map(_.track).size,
          ts.map(_.startDate).distinct.size
        )
    }
  }
  val dailyQueryAsc = quote { user: Username =>
    dailyQuery(user).sortBy(_.date)(Ord.asc)
  }
  val dailyQueryDesc = quote { user: Username =>
    dailyQuery(user).sortBy(_.date)(Ord.desc)
  }
  val monthlyQuery = quote { user: Username =>
    tracksBy(user).groupBy(t => (t.startYear, t.startMonth)).map {
      case ((year, month), ts) =>
        MonthlyAggregates(
          year,
          month,
          ts.map(_.distance).sum,
          ts.map(_.duration).sum,
          ts.map(_.track).size,
          ts.map(_.startDate).distinct.size
        )
    }
  }
  val monthlyQueryAsc = quote { user: Username =>
    monthlyQuery(user).sortBy(m => (m.year, m.month))(Ord.asc)
  }
  val monthlyQueryDesc = quote { user: Username =>
    monthlyQuery(user).sortBy(m => (m.year, m.month))(Ord.desc)
  }
  val yearlyQuery = quote { user: Username =>
    tracksBy(user).groupBy(t => t.startYear).map {
      case (year, ts) =>
        YearlyAggregates(
          year,
          ts.map(_.distance).sum,
          ts.map(_.duration).sum,
          ts.map(_.track).size,
          ts.map(_.startDate).distinct.size
        )
    }
  }
  val yearlyQueryAsc = quote { user: Username =>
    yearlyQuery(user).sortBy(_.year)(Ord.asc)
  }
  val yearlyQueryDesc = quote { user: Username =>
    yearlyQuery(user).sortBy(_.year)(Ord.desc)
  }
  val allTimeQuery = quote { user: Username =>
    tracksBy(user).groupBy(_.boat.username).map {
      case (_, ts) =>
        AllTimeAggregates(
          ts.map(_.startDate).min,
          ts.map(_.startDate).max,
          ts.map(_.distance).sum,
          ts.map(_.duration).sum,
          ts.map(_.track).size,
          ts.map(_.startDate).distinct.size
        )
    }
  }
}
