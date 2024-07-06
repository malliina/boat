package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{Hidden, ModalId}
import com.malliina.boat.{AboutKeys, FrontKeys, Language, ProfileLang, Usernames}
import com.malliina.values.Username
import scalatags.Text.all.*

class About(lang: WebLang, profile: ProfileLang) extends AboutKeys:
  val empty = modifier()

  def about(user: Username, language: Language) =
    val isLoggedIn = user != Usernames.anon
    div(id := ModalId, `class` := s"${FrontKeys.Modal} $Hidden")(
      div(`class` := "modal-content")(
        i(`class` := "about close"),
        if isLoggedIn then
          modifier(
            h2(`class` := "text-center")(profile.username),
            p(`class` := "text-center")(s"${profile.signedInAs} $user."),
            hr(`class` := "modal-divider"),
            h2(`class` := "text-center")(profile.language),
            radios(
              LanguageRadios,
              Seq(
                RadioOptions(
                  "radio-se",
                  Language.swedish.code,
                  profile.swedish,
                  language == Language.swedish
                ),
                RadioOptions(
                  "radio-fi",
                  Language.finnish.code,
                  profile.finnish,
                  language == Language.finnish
                ),
                RadioOptions(
                  "radio-en",
                  Language.english.code,
                  profile.english,
                  language == Language.english
                )
              )
            ),
            hr(`class` := "modal-divider")
          )
        else empty,
        h2(`class` := "badges-title")(lang.getTheApp),
        div(`class` := "badges")(
          div(`class` := "badge-wrapper-android")(
            a(
              `class` := "badge-android",
              href := "https://play.google.com/store/apps/details?id=com.malliina.boattracker&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1"
            )
          ),
          div(`class` := "badge-wrapper-ios")(
            a(
              `class` := "badge-ios",
              href := "https://itunes.apple.com/us/app/boat-tracker/id1434203398?ls=1&mt=8"
            )
          )
        ),
        hr(`class` := "modal-divider"),
        h2(lang.maritimeData),
        p(
          a(href := "https://creativecommons.org/licenses/by/4.0/")("CC 4.0"),
          " ",
          lang.disclaimer
        ),
        h2("Font Awesome"),
        p(a(href := "https://fontawesome.com")("fontawesome.com")),
        h2("POIJU.IO"),
        p(a(href := "https://github.com/iaue/poiju.io")("POIJU.IO")),
        if isLoggedIn then
          modifier(
            hr(`class` := "modal-divider"),
            div(`class` := "button-container")(
              a(`type` := "button", `class` := "btn btn-sm btn-danger", href := "/sign-out")(
                profile.logout
              )
            )
          )
        else empty
      )
    )

  case class RadioOptions(id: String, value: String, label: String, checked: Boolean)

  private def radios(groupName: String, rs: Seq[RadioOptions]) =
    div(`class` := "language-form")(rs.map: radio =>
      div(`class` := "form-check form-check-inline")(
        input(
          `class` := "form-check-input",
          `type` := "radio",
          name := groupName,
          id := radio.id,
          value := radio.value,
          if radio.checked then checked else empty
        ),
        label(`class` := "form-check-label", `for` := radio.id)(radio.label)
      ))
