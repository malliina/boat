package com.malliina.boat

import com.malliina.http.HttpClient
import org.scalajs.dom.HTMLInputElement

class AboutPage extends BaseFront:
  document.getElementsByName(LanguageRadios).foreach { radio =>
    radio.addOnClick { e =>
      val code = e.target.asInstanceOf[HTMLInputElement].value
      langChanged(Language(code))
    }
  }

  def langChanged(to: Language): Unit =
    HttpClient.put[ChangeLanguage, SimpleMessage]("/users/me", ChangeLanguage(to))
