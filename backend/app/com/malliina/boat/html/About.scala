package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{Hidden, ModalId}
import com.malliina.boat.{AboutKeys, FrontKeys, Language, Usernames}
import com.malliina.values.Username
import scalatags.Text.all._

object About {
  def apply(lang: WebLang) = new About(lang)
}

class About(lang: WebLang) extends AboutKeys {
  val empty = modifier()

  def about(user: Username, language: Language) = {
    div(id := ModalId, `class` := s"${FrontKeys.Modal} $Hidden")(
      div(`class` := "modal-content")(
        i(`class` := "close fas fa-times"),
        if (user != Usernames.anon) {
          modifier(
            p(`class` := "text-center", s"${lang.signedInAs} $user."),
            radios(
              LanguageRadios,
              Seq(
                RadioOptions("radio-se",
                             Language.swedish.code,
                             lang.swedish,
                             language == Language.swedish),
                RadioOptions("radio-fi",
                             Language.finnish.code,
                             lang.finnish,
                             language == Language.finnish),
                RadioOptions("radio-en",
                             Language.english.code,
                             lang.english,
                             language == Language.english)
              )
            ),
            hr(`class` := "modal-divider")
          )
        } else {
          empty
        },
        a(`class` := "badge-ios",
          href := "https://itunes.apple.com/us/app/boat-tracker/id1434203398?ls=1&mt=8"),
        hr(`class` := "modal-divider"),
        h2(lang.maritimeData),
        p(a(href := "https://creativecommons.org/licenses/by/4.0/")("CC 4.0"),
          " ",
          lang.disclaimer),
        h2("Font Awesome"),
        p(a(href := "https://fontawesome.com")("fontawesome.com")),
        h2("POIJU.IO"),
        p(a(href := "https://github.com/iaue/poiju.io")("POIJU.IO"))
      )
    )
  }

  case class RadioOptions(id: String, value: String, label: String, checked: Boolean)

  def radios(groupName: String, rs: Seq[RadioOptions]) = div(rs.map { radio =>
    div(`class` := "form-check form-check-inline")(
      input(`class` := "form-check-input",
            `type` := "radio",
            name := groupName,
            id := radio.id,
            value := radio.value,
            if (radio.checked) checked else empty),
      label(`class` := "form-check-label", `for` := radio.id)(radio.label)
    )
  })
}
