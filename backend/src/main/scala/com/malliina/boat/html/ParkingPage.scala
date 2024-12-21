package com.malliina.boat.html

import com.malliina.boat.FrontKeys.MapId
import scalatags.Text.all.*

object ParkingPage extends BoatSyntax:
  def apply(lang: BoatLang) =
    div(cls := "container parking")(
      h1("Parking"),
      div(id := MapId, cls := s"mapbox-map")
    )
