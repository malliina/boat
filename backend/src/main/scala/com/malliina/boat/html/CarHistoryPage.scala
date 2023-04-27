package com.malliina.boat.html

import com.malliina.boat.{-, CarDrive, Energy, wattHours}
import com.malliina.util.AppLogger
import scalatags.Text.all.*

object CarHistoryPage extends BoatImplicits:
  private val log = AppLogger(getClass)

  val empty = modifier()

  def apply(drives: List[CarDrive]) = modifier(
    p(s"${drives.size} drives."),
    drives.filter(_.updates.size > 30).flatMap { d =>
      val us = d.updates
      for
        h <- us.headOption
        t <- us.lastOption
      yield
        val start = h.carTime.dateTime
        val end = t.carTime.dateTime
        val minBat = us.minBy(_.batteryLevel).batteryLevel
        val maxBat = us.maxBy(_.batteryLevel).batteryLevel
        log.info(s"Min $minBat max $maxBat")
        val energyConsumed: Option[Energy] = for
          batteryStart <- h.batteryLevel
          batteryEnd <- t.batteryLevel
        yield batteryStart - batteryEnd
        modifier(
          div(s"$start - $end with ${d.updates.size} updates by ${d.car.name}"),
          energyConsumed.fold(empty)(e =>
            val whStr = "%.2f".format(e.wattHours)
            div(s"Energy consumed $whStr watt hours")
          )
        )
    }
  )
