package com.malliina.boat.html

import com.malliina.boat.SettingsLang
import com.malliina.boat.auth.AuthProvider
import com.malliina.boat.http4s.Reverse
import com.malliina.html.Bootstrap
import com.malliina.html.Tags
import scalatags.Text.all.*

object SignInPage extends Bootstrap(Tags(scalatags.Text)) with BoatImplicits:
  val reverse = Reverse

  def apply(lang: SettingsLang) =
    div(cls := "container auth-form ml-auto mr-auto")(
      row(
        div(cls := col.md.twelve)(
          h1(lang.signIn)
        )
      ),
      row(
        div(cls := s"${col.md.twelve} social-container")(
          socialButton(AuthProvider.Google, s"${lang.signInWith} Google"),
          socialButton(AuthProvider.Microsoft, s"${lang.signInWith} Microsoft"),
          socialButton(AuthProvider.Apple, s"${lang.signInWith} Apple")
        )
      )
    )

  private def socialButton(provider: AuthProvider, linkText: String) =
    a(cls := s"social-button $provider", href := reverse.signInFlow(provider))(
      span(cls := s"social-logo $provider"),
      span(cls := "social-text", linkText)
    )
