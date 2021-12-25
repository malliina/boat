package com.malliina.boat.html

import com.malliina.boat.{Lang, SettingsLang}
import com.malliina.boat.auth.AuthProvider
import com.malliina.boat.http4s.Reverse
import scalatags.Text.all.*

object SignInPage extends BoatImplicits:
  val reverse = Reverse

  def apply(lang: SettingsLang) = div(`class` := "container auth-form ml-auto mr-auto")(
    div(`class` := "row")(
      div(`class` := "col-md-12")(
        h1(lang.signIn)
      )
    ),
    div(`class` := "row social-container")(
      socialButton(AuthProvider.Google, s"${lang.signInWith} Google"),
      socialButton(AuthProvider.Microsoft, s"${lang.signInWith} Microsoft"),
      socialButton(AuthProvider.Apple, s"${lang.signInWith} Apple")
    )
  )

  def socialButton(provider: AuthProvider, linkText: String) =
    a(`class` := s"social-button $provider", href := reverse.signInFlow(provider))(
      span(`class` := s"social-logo $provider"),
      span(`class` := "social-text", linkText)
    )
