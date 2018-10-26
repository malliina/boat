package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{ChartsClass, ChartsId}
import com.malliina.boat.TrackRef
import com.malliina.html.Tags
import scalatags.Text.all._

object Charts extends Tags(scalatags.Text) {
  // https://cdnjs.com/libraries/Chart.js

  def chart(track: TrackRef) = page(
    TrackList.namedInfoBox(track),
    canvas(id := ChartsId, `class` := "charts-canvas", width := "400", height := "400")
  )

  private def page(content: Modifier*) = PageConf(
    div(`class` := "container-fluid")(content),
    bodyClasses = Seq(ChartsClass),
    js = modifier(jsScript("https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.7.2/Chart.bundle.js"))
  )
}
