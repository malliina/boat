package com.malliina.boat

abstract class Lang(val name: String,
                    val location: String,
                    val `type`: String,
                    val navigation: String,
                    val construction: String,
                    val speed: String,
                    val water: String,
                    val depth: String) {
  def fairway: FairwayLang
}

object Finnish extends Lang("Nimi", "Sijainti", "Tyyppi", "Navigointi", "Rakenne", "Nopeus", "Vesi", "Syvyys") {
  override val fairway = FairwayLang(
    "Laatuluokka", "Väyläalueen tyyppi", "Väyläalueen syvyys",
    "Haraussyvyys", "Vertaustaso", "Väylän tila",
    "Merkin laji")
}

object Swedish extends Lang("Namn", "Plats", "Typ", "Navigering", "Struktur", "Hastighet", "Vatten", "Djup") {
  override val fairway = FairwayLang("", "", "", "", "", "", "")
}

case class FairwayLang(qualityClass: String,
                       fairwayType: String,
                       fairwayDepth: String,
                       harrowDepth: String,
                       comparisonLevel: String,
                       state: String,
                       markType: String)
