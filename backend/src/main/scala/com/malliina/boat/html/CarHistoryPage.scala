package com.malliina.boat.html

import com.malliina.boat.http.{CarQuery, Limits, TimeRange}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{-, CarDrive, Energy, wattHours}
import com.malliina.measure.DistanceM
import com.malliina.util.AppLogger
import scalatags.Text.all.*
import java.time.Instant

object CarHistoryPage extends BoatImplicits:
  private val log = AppLogger(getClass)

  val empty = modifier()

  def apply(drives: List[CarDrive]) = div(`class` := "drives-container")(
    h2("History"),
    p(s"${drives.size} drives."),
    table(`class` := "table table-hover drives-table")(
      thead(
        tr(
          th("Car"),
          th("Distance (km)"),
          th("Energy (kWh)"),
          th("Start"),
          th("End"),
          th("URL")
        )
      ),
      tbody(
        drives.filter(_.updates.size > 30).reverse.flatMap { d =>
          val us = d.updates
          for
            h <- us.headOption
            t <- us.lastOption
          yield
            val start = h.carTime.dateTime
            val end = t.carTime.dateTime
            val q = CarQuery(
              Limits.default,
              TimeRange(
                Option(Instant.ofEpochMilli(h.carTime.millis)),
                Option(Instant.ofEpochMilli(t.carTime.millis))
              ),
              Nil
            )
            val energyConsumed: Option[Energy] = for
              batteryStart <- h.batteryLevel
              batteryEnd <- t.batteryLevel
            yield batteryStart - batteryEnd
            val meters = d.updates.map(_.diff.meters).sum
            tr(
              td(d.car.name),
              td("%.2f".format(meters / 1000)),
              td(energyConsumed.map(e => "%.2f".format(e.wattHours / 1000)).getOrElse("N/A")),
              td(start),
              td(end),
              td(a(href := Reverse.history(q))("Show"))
            )
        }
      )
    )
  )
