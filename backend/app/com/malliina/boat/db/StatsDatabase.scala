package com.malliina.boat.db

import com.malliina.boat.http.{SortOrder, TrackQuery}
import com.malliina.boat.{DateVal, Lang, MinimalUserInfo, MonthlyStats, Stats, StatsResponse, YearlyStats}
import com.malliina.measure.DistanceM
import io.getquill.SnakeCase

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import concurrent.duration.DurationInt

object StatsDatabase {
  def apply(db: BoatDatabase[SnakeCase]): StatsDatabase = new StatsDatabase(db)
}

class StatsDatabase(db: BoatDatabase[SnakeCase]) extends StatsSource {
  import db._
  implicit val exec = db.ec

  def stats(user: MinimalUserInfo, limits: TrackQuery, lang: Lang): Future[StatsResponse] = Future {
    val username = user.username
    val isAsc = limits.order == SortOrder.Asc
    val task = for {
      daily <- if (isAsc) runIO(dailyQueryAsc(lift(username)))
      else runIO(dailyQueryDesc(lift(username)))
      monthly <- if (isAsc) runIO(monthlyQueryAsc(lift(username)))
      else runIO(monthlyQueryDesc(lift(username)))
      yearly <- if (isAsc) runIO(yearlyQueryAsc(lift(username)))
      else runIO(yearlyQueryDesc(lift(username)))
      allTime <- runIO(allTimeQuery(lift(username)))
    } yield {
      val all = allTime.headOption.getOrElse(AllTimeAggregates.empty)
      val now = DateVal.now()
      val zeroDistance = DistanceM.zero
      val zeroDuration: FiniteDuration = 0.seconds
      val months = monthly.map { ma =>
        MonthlyStats(
          lang.calendar.months(ma.month),
          ma.year,
          ma.month,
          ma.tracks,
          ma.distance.getOrElse(zeroDistance),
          ma.duration.getOrElse(zeroDuration),
          ma.days
        )
      }
      StatsResponse(
        daily.map { da =>
          Stats(
            da.date.iso8601,
            da.date,
            da.date.plusDays(1),
            da.tracks,
            da.distance.getOrElse(zeroDistance),
            da.duration.getOrElse(zeroDuration),
            da.days
          )
        },
        yearly.map { ya =>
          YearlyStats(
            ya.year.toString,
            ya.year,
            ya.tracks,
            ya.distance.getOrElse(zeroDistance),
            ya.duration.getOrElse(zeroDuration),
            ya.days,
            months.filter(_.year == ya.year)
          )
        },
        Stats(
          lang.labels.allTime,
          all.from.getOrElse(now),
          all.to.getOrElse(now),
          all.tracks,
          all.distance.getOrElse(zeroDistance),
          all.duration.getOrElse(zeroDuration),
          all.days
        )
      )
    }
    perform("Statistics", task)
  }

}
