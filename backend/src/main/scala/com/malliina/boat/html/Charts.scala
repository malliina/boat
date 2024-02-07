package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{ChartsClass, ChartsId, FormsClass}
import com.malliina.boat.TrackRef
import com.malliina.html.Tags
import scalatags.Text.all.*

object Charts extends Tags(scalatags.Text):
  // https://cdnjs.com/libraries/Chart.js
  def chart(track: TrackRef, lang: BoatLang) = page(
    div(cls := "container")(
      SentencesPage.namedInfoBox(track, lang)
    ),
    div(cls := "container-fluid")(
      canvas(id := ChartsId, cls := "charts-canvas", width := "400", height := "400")
    )
  )

  private def page(content: Modifier*) = PageConf(
    content,
    bodyClasses = Seq(ChartsClass, FormsClass)
  )
