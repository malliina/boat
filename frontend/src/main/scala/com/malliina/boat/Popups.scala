package com.malliina.boat

import com.malliina.measure.{Distance, Speed}
import scalatags.JsDom.all._

object Popups {
  def apply(lang: Lang) = new Popups(lang)
}

class Popups(lang: Lang) {
  val empty = modifier()

  def track(c: TimedCoord, from: TrackRef) = {
    val kn = "%.2f".format(c.speed.toKnots)
    titledTable(from.boatName.name)(
      row(lang.speed, s"$kn kn"),
      row(lang.water, c.waterTemp.formatCelsius),
      row(lang.depth, c.depth.short),
      tr(td(colspan := 2)(c.boatTime.dateTime))
    )
  }

  def mark(symbol: MarineSymbol) =
    titledTable(symbol.owner)(
      row(lang.`type`, symbol.aidType.in(lang)),
      symbol.construction.fold(empty)(c => row(lang.construction, c.in(lang))),
      if (symbol.navMark == NavMark.NotApplicable) empty
      else row(lang.navigation, symbol.navMark.in(lang)),
      symbol.name(lang).fold(empty)(n => row(lang.name, n)),
      symbol.location(lang).fold(empty)(l => row(lang.location, l))
    )

  def fairway(fairway: FairwayArea) = {
    val labels = lang.fairway
    titledTable(fairway.owner)(
      row(labels.fairwayType, fairway.fairwayType.in(lang)),
      row(labels.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(labels.harrowDepth, asMeters(fairway.harrowDepth)),
      fairway.markType.fold(empty)(markType => row(labels.markType, markType.in(lang)))
    )
  }

  def depthArea(depthArea: DepthArea) = {
    val labels = lang.depths
    popupTable(
      row(labels.minDepth, asMeters(depthArea.minDepth)),
      row(labels.maxDepth, asMeters(depthArea.maxDepth))
    )
  }

  private def asMeters(d: Distance) = {
    val value = "%.2f".format(d.toMetersDouble)
      .stripSuffix("0")
      .stripSuffix("0")
      .stripSuffix(".")
    s"$value m"
  }

  private def titledTable(title: String)(content: Modifier*) =
    popupTable(
      tr(td(colspan := 2)(title)),
      content
    )

  private def popupTable(content: Modifier*) =
    table(`class` := "boat-popup")(
      tbody(
        content
      )
    )

  private def row(title: String, value: String) = tr(td(`class` := "popup-title")(title), td(value))

  def marker(speed: Speed) =
    i(`class` := "fas fa-trophy marker-top-speed")
}
