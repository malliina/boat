package com.malliina.boat

import org.scalajs.dom.html.Table
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._

object BoatHtml {
  def popup(c: TimedCoord, from: TrackRef): TypedTag[Table] = {
    val kn = "%.2f".format(c.speed.toKnots)
    table(`class` := "boat-popup")(
      tbody(
        tr(td(colspan := 2)(from.boatName.name)),
        tr(td(`class` := "popup-title")("Speed"), td(s"$kn kn")),
        tr(td(`class` := "popup-title")("Water"), td(c.waterTemp.formatCelsius)),
        tr(td(`class` := "popup-title")("Depth"), td(c.depth.short)),
        tr(td(colspan := 2)(c.boatTime))
      )
    )
  }
}
