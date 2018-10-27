package com.malliina.boat.html

import com.malliina.boat.FrontKeys
import com.malliina.boat.FrontKeys.{Hidden, ModalId}
import scalatags.Text.all._

object Modal {
  def about = div(id := ModalId, `class` := s"${FrontKeys.Modal} $Hidden")(
    div(`class` := "modal-content")(
      span(`class` := "close")(raw("&times;")),
      a(`class` := "badge-ios", href := "https://itunes.apple.com/us/app/boat-tracker/id1434203398?ls=1&mt=8"),
      hr(`class` := "modal-divider"),
      h2("Merikartta-aineistot"),
      p(a(href := "https://creativecommons.org/licenses/by/4.0/")("CC 4.0"), " ", "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia."),
      h2("Java Marine API"),
      p(a(href := "http://www.gnu.org/licenses/lgpl-3.0-standalone.html")("GNU LGPL"), " ", a(href := "https://ktuukkan.github.io/marine-api/")("https://ktuukkan.github.io/marine-api/")),
      h2("Open Iconic"),
      p("Open Iconic — ", a(href := "https://www.useiconic.com/open")("www.useiconic.com/open")),
      h2("Inspiration"),
      p("Inspired by ", a(href := "https://github.com/iaue/poiju.io")("POIJU.IO"), ".")
    )
  )
}
