package com.malliina.boat

import cats.effect.Async
import com.malliina.http.Http
import org.scalajs.dom.HTMLInputElement

class AboutPage[F[_]: Async](http: Http[F]) extends BaseFront:
  document
    .getElementsByName(LanguageRadios)
    .foreach: radio =>
      radio.addOnClick: e =>
        val code = e.target.asInstanceOf[HTMLInputElement].value
        langChanged(Language(code))

  private def langChanged(to: Language): Unit = http.using: client =>
    client.put[ChangeLanguage, SimpleMessage]("/users/me", ChangeLanguage(to))
