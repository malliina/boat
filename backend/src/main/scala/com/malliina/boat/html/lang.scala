package com.malliina.boat.html

import com.malliina.boat.{Lang, Language}

abstract class BoatLang(val web: WebLang, val lang: Lang)

object BoatLang:
  val default = Fi

  def apply(lang: Language): BoatLang = lang match
    case Language.Finnish => Fi
    case Language.Swedish => Se
    case Language.English => En

  object Fi extends BoatLang(WebLang.Fi, Lang.fi)
  object Se extends BoatLang(WebLang.Se, Lang.se)
  object En extends BoatLang(WebLang.En, Lang.en)

case class PaginationLang(previous: String, next: String, first: String, last: String)

case class WebLang(
  getTheApp: String,
  maritimeData: String,
  disclaimer: String,
  signedInAs: String,
  finnish: String,
  swedish: String,
  english: String,
  titlePlaceholder: String,
  commentsPlaceholder: String,
  save: String,
  cancel: String,
  editTitle: String,
  editComments: String,
  pagination: PaginationLang
)

object WebLang:
  val Fi = WebLang(
    "Lataa sovellus",
    "Merikartta-aineistot",
    "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia.",
    "Käyttäjätunnus",
    "Suomeksi",
    "Ruotsiksi",
    "Englanniksi",
    "Iltareissu",
    "Hauskaa oli",
    "Tallenna",
    "Peruuta",
    "Muokkaa nimeä",
    "Muokkaa kommentteja",
    PaginationLang("Edellinen", "Seuraava", "Ensimmäinen", "Viimeinen")
  )
  val Se = WebLang(
    "Ladda appen",
    "Sjökortsmaterial",
    "Källa: Trafikverket. Får inte användas för navigationsändamål. Uppfyller inte fordringarna för officiella sjökort.",
    "Inloggad som",
    "Finska",
    "Svenska",
    "Engelska",
    "Kvällstripp",
    "Jätte roligt",
    "Spara",
    "Avbryt",
    "Redigera namn",
    "Redigera kommentarer",
    PaginationLang("Föregående", "Följande", "Första", "Sista")
  )
  val En = WebLang(
    "Get the app",
    "Nautical charts",
    "Source: Finnish Transport Agency. Not for navigational use. Does not meet the requirements for official nautical charts.",
    "Signed in as",
    "Finnish",
    "Swedish",
    "English",
    "Evening trip",
    "It was fun",
    "Save",
    "Cancel",
    "Edit title",
    "Edit comments",
    PaginationLang("Previous", "Next", "First", "Last")
  )
  val default = Fi
