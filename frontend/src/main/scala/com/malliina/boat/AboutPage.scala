package com.malliina.boat

import com.malliina.http.Http
import org.scalajs.dom.HTMLInputElement

class AboutPage[F[_]](http: Http[F], val log: BaseLogger = BaseLogger.console) extends BaseFront:
  document
    .getElementsByName(LanguageRadios)
    .foreach: radio =>
      radio.addOnClick: e =>
        val code = e.target.asInstanceOf[HTMLInputElement].value
        Language
          .build(code)
          .fold(err => log.error(err.message), lang => langChanged(lang))

  private def langChanged(to: Language): Unit = http.using: client =>
    client.put[ChangeLanguage, SimpleMessage]("/users/me", ChangeLanguage(to))
