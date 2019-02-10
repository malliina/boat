package com.malliina.boat.html

import com.malliina.boat.{Lang, Language}

abstract class BoatLang(val web: WebLang, val lang: Lang)

object BoatLang {
  val default = Fi

  def apply(lang: Language) = lang match {
    case Language.finnish => Fi
    case Language.swedish => Se
    case Language.english => En
    case _ => default
  }

  object Fi extends BoatLang(WebLang.Fi, Lang.fi)

  object Se extends BoatLang(WebLang.Se, Lang.se)

  object En extends BoatLang(WebLang.En, Lang.en)

}

trait WebLang {
  val maritimeData: String
  val disclaimer: String
  val signedInAs: String
  val finnish: String
  val swedish: String
  val english: String
}

object WebLang {
  val default = Fi

  object Fi extends WebLang {
    val maritimeData = "Merikartta-aineistot"
    val disclaimer = "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia."
    val signedInAs = "Käyttäjätunnus"
    val finnish = "Suomeksi"
    val swedish = "Ruotsiksi"
    val english = "Englanniksi"
  }

  object Se extends WebLang {
    val maritimeData = "Sjökortsmaterial"
    val disclaimer = "Källa: Trafikverket. Får inte användas för navigationsändamål. Uppfyller inte fordringarna för officiella sjökort."
    val signedInAs = "Inloggad som"
    val finnish = "Finska"
    val swedish = "Svenska"
    val english = "Engelska"
  }

  object En extends WebLang {
    val maritimeData = "Nautical charts"
    val disclaimer = "Source: Finnish Transport Agency. Not for navigational use. Does not meet the requirements for official nautical charts."
    val signedInAs = "Signed in as"
    val finnish = "Finnish"
    val swedish = "Swedish"
    val english = "English"
  }

}
