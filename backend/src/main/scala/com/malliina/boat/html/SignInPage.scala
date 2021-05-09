package com.malliina.boat.html

import com.malliina.boat.Lang
import com.malliina.boat.auth.AuthProvider
import com.malliina.boat.http4s.Reverse
import scalatags.Text.all._

object SignInPage extends BoatImplicits {
  val reverse = Reverse

  def apply(lang: Lang) = div(`class` := "container auth-form ml-auto mr-auto")(
    div(`class` := "row")(
      div(`class` := "col-md-12")(
        h1(lang.settings.signIn)
      )
    ),
    div(`class` := "row social-container")(
      socialButton(AuthProvider.Google, "Sign in with Google"),
      socialButton(AuthProvider.Microsoft, "Sign in with Microsoft")
    )
  )
  def socialButton(provider: AuthProvider, linkText: String) =
    a(`class` := s"social-button $provider", href := reverse.signInFlow(provider))(
      span(`class` := s"social-logo $provider"),
      span(`class` := "social-text", linkText)
    )
}
