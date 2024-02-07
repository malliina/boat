package com.malliina.boat.html

import com.malliina.boat.SettingsLang
import com.malliina.boat.auth.AuthProvider
import com.malliina.boat.http4s.Reverse
import scalatags.Text.all.*

object SignInPage extends BoatImplicits:
  val reverse = Reverse

  def apply(lang: SettingsLang) =
    div(cls := "container auth-form ml-auto mr-auto")(
      div(cls := "row")(
        div(cls := "col-md-12")(
          h1(lang.signIn)
        )
      ),
      div(cls := "row social-container")(
        socialButton(AuthProvider.Google, s"${lang.signInWith} Google"),
        socialButton(AuthProvider.Microsoft, s"${lang.signInWith} Microsoft"),
        socialButton(AuthProvider.Apple, s"${lang.signInWith} Apple")
      )
    )

  private def socialButton(provider: AuthProvider, linkText: String) =
    a(cls := s"social-button $provider", href := reverse.signInFlow(provider))(
      span(cls := s"social-logo $provider"),
      span(cls := "social-text", linkText)
    )
