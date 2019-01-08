package com.malliina.boat.html

import com.malliina.boat.{AboutKeys, FrontKeys, Language, Usernames}
import com.malliina.boat.FrontKeys.{Hidden, ModalId}
import com.malliina.values.Username
import scalatags.Text.all._

object About extends AboutKeys {
  val empty = modifier()

  def about(user: Username, language: Language) =
    div(id := ModalId, `class` := s"${FrontKeys.Modal} $Hidden")(
      div(`class` := "modal-content")(
        i(`class` := "close fas fa-times"),
        if (user != Usernames.anon) {
          modifier(
            p(`class` := "text-center", s"Signed in as $user."),
            radios(LanguageRadios, Seq(
              RadioOptions("radio-se", Language.swedish.code, "Swedish", language == Language.swedish),
              RadioOptions("radio-fi", Language.finnish.code, "Finnish", language == Language.finnish),
              RadioOptions("radio-en", Language.english.code, "English", language == Language.english)
            )),
            hr(`class` := "modal-divider")
          )
        } else {
          empty
        },
        a(`class` := "badge-ios", href := "https://itunes.apple.com/us/app/boat-tracker/id1434203398?ls=1&mt=8"),
        hr(`class` := "modal-divider"),
        h2("Merikartta-aineistot"),
        p(a(href := "https://creativecommons.org/licenses/by/4.0/")("CC 4.0"), " ", "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia."),
        h2("Java Marine API"),
        p(a(href := "http://www.gnu.org/licenses/lgpl-3.0-standalone.html")("GNU LGPL"), " ", a(href := "https://ktuukkan.github.io/marine-api/")("https://ktuukkan.github.io/marine-api/")),
        h2("Open Iconic"),
        p(a(href := "https://www.useiconic.com/open")("www.useiconic.com/open")),
        h2("Font Awesome"),
        p(a(href := "https://fontawesome.com")("fontawesome.com")),
        h2("Inspiration"),
        p("Inspired by ", a(href := "https://github.com/iaue/poiju.io")("POIJU.IO"), ".")
      )
    )

  case class RadioOptions(id: String, value: String, label: String, checked: Boolean)

  def radios(groupName: String, rs: Seq[RadioOptions]) = div(
    rs.map { radio =>
      div(`class` := "form-check form-check-inline")(
        input(`class` := "form-check-input", `type` := "radio", name := groupName, id := radio.id, value := radio.value, if (radio.checked) checked else empty),
        label(`class` := "form-check-label", `for` := radio.id)(radio.label)
      )
    })
}
