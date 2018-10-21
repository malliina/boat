package com.malliina.boat

import com.malliina.measure.Speed
import org.scalajs.dom.html.Table
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._

object BoatHtml {
  val empty = modifier()

  def trackPopup(c: TimedCoord, from: TrackRef, lang: Lang = Finnish): TypedTag[Table] = {
    val kn = "%.2f".format(c.speed.toKnots)
    table(`class` := "boat-popup")(
      tbody(
        tr(td(colspan := 2)(from.boatName.name)),
        row(lang.speed, s"$kn kn"),
        row(lang.water, c.waterTemp.formatCelsius),
        row(lang.depth, c.depth.short),
        tr(td(colspan := 2)(c.boatTime))
      )
    )
  }

  def markPopup(symbol: MarineSymbol, lang: Lang = Finnish) =
    table(`class` := "boat-popup")(
      tbody(
        tr(td(colspan := 2)(symbol.owner)),
        row(lang.`type`, symbol.aidType.in(lang)),
        symbol.construction.fold(empty)(c => row(lang.construction, c.in(lang))),
        if (symbol.navMark == NavMark.NotApplicable) empty
        else row(lang.navigation, symbol.navMark.in(lang)),
        row(lang.name, symbol.name(lang)),
        row(lang.location, symbol.location(lang))
      )
    )

  private def row(title: String, value: String) = tr(td(`class` := "popup-title")(title), td(value))

  def marker(speed: Speed) =
    i(`class` := "fas fa-trophy marker-top-speed")
}
