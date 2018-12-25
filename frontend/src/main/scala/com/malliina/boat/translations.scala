package com.malliina.boat

abstract class Lang(val name: String,
                    val location: String,
                    val `type`: String,
                    val navigation: String,
                    val construction: String,
                    val speed: String,
                    val water: String,
                    val depth: String,
                    val influence: String) {
  def fairway: FairwayLang

  def depths: DepthLang
}

object Lang {
  object Finnish extends Lang("Nimi", "Sijainti", "Tyyppi", "Navigointi", "Rakenne", "Nopeus", "Vesi", "Syvyys", "Vaikutusalue") {
    override val fairway = FairwayLang(
      "Laatuluokka", "Väyläalueen tyyppi", "Väyläalueen syvyys",
      "Haraussyvyys", "Vertaustaso", "Väylän tila",
      "Merkin laji")
    override val depths = DepthLang("Syvyys min", "Syvyys max")
  }

  object Swedish extends Lang("Namn", "Plats", "Typ", "Navigering", "Struktur", "Hastighet", "Vatten", "Djup", "Område") {
    override val fairway = FairwayLang("", "", "", "", "", "", "")
    override val depths = DepthLang("Djup min", "Djup max")
  }
}

case class FairwayLang(qualityClass: String,
                       fairwayType: String,
                       fairwayDepth: String,
                       harrowDepth: String,
                       comparisonLevel: String,
                       state: String,
                       markType: String)

case class DepthLang(minDepth: String, maxDepth: String)
