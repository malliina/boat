package com.malliina.boat

import com.malliina.measure.{Distance, Speed}
import scalatags.JsDom.all._

object BoatHtml {
  val empty = modifier()

  def trackPopup(c: TimedCoord, from: TrackRef, lang: Lang = Finnish) = {
    val kn = "%.2f".format(c.speed.toKnots)
    popupTable(from.boatName.name)(
      row(lang.speed, s"$kn kn"),
      row(lang.water, c.waterTemp.formatCelsius),
      row(lang.depth, c.depth.short),
      tr(td(colspan := 2)(c.boatTime))
    )
  }

  def markPopup(symbol: MarineSymbol, lang: Lang = Finnish) =
    popupTable(symbol.owner)(
      row(lang.`type`, symbol.aidType.in(lang)),
      symbol.construction.fold(empty)(c => row(lang.construction, c.in(lang))),
      if (symbol.navMark == NavMark.NotApplicable) empty
      else row(lang.navigation, symbol.navMark.in(lang)),
      symbol.name(lang).fold(empty)(n => row(lang.name, n)),
      symbol.location(lang).fold(empty)(l => row(lang.location, l))
    )

  def fairwayPopup(fairway: FairwayArea, lang: Lang = Finnish) = {
    val labels = lang.fairway
    popupTable(fairway.owner)(
      row(labels.fairwayType, fairway.fairwayType.in(lang)),
      row(labels.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(labels.harrowDepth, asMeters(fairway.harrowDepth)),
      fairway.markType.fold(empty)(markType => row(labels.markType, markType.in(lang)))
    )
  }

  private def asMeters(d: Distance) = {
    val value = "%.2f".format(d.toMetersDouble)
      .stripSuffix("0")
      .stripSuffix("0")
      .stripSuffix(".")
    s"$value m"
  }

  private def popupTable(title: String)(content: Modifier*) =
    table(`class` := "boat-popup")(
      tbody(
        tr(td(colspan := 2)(title)),
        content
      )
    )

  private def row(title: String, value: String) = tr(td(`class` := "popup-title")(title), td(value))

  def marker(speed: Speed) =
    i(`class` := "fas fa-trophy marker-top-speed")
}
