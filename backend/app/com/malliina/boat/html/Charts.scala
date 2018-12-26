package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{ChartsClass, ChartsId}
import com.malliina.boat.TrackRef
import com.malliina.html.Tags
import scalatags.Text.all._

object Charts extends Tags(scalatags.Text) {
  // https://cdnjs.com/libraries/Chart.js
  def chart(track: TrackRef) = page(
    div(`class` := "container")(
      SentencesPage.namedInfoBox(track)
    ),
    div(`class` := "container-fluid")(
      canvas(id := ChartsId, `class` := "charts-canvas", width := "400", height := "400")
    )
  )

  private def page(content: Modifier*) = PageConf(
    content,
    bodyClasses = Seq(ChartsClass)
  )
}
