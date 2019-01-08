package com.malliina.boat

sealed abstract class Lang(val transportAgency: String,
                           val defenceForces: String,
                           val portOfHelsinki: String,
                           val cityOfHelsinki: String,
                           val cityOfEspoo: String,
                           val name: String,
                           val location: String,
                           val `type`: String,
                           val navigation: String,
                           val construction: String,
                           val speed: String,
                           val water: String,
                           val depth: String,
                           val influence: String,
                           val top: String,
                           val duration: String,
                           val tracks: String,
                           val qualityClass: String,
                           val fairwayType: String,
                           val fairwayDepth: String,
                           val harrowDepth: String,
                           val comparisonLevel: String,
                           val state: String,
                           val markType: String,
                           val minDepth: String,
                           val maxDepth: String)

object Lang {
  val default = Finnish

  def apply(language: Language): Lang = language match {
    case Language.swedish => Swedish
    case Language.finnish => Finnish
    case Language.english => English
    case _ => default
  }

  object English extends Lang(
    "Finnish Transport Agency",
    "Defence forces",
    "Port of Helsinki",
    "City of Helsinki",
    "City of Espoo",
    "Name",
    "Location",
    "Type",
    "Navigation",
    "Structure",
    "Speed",
    "Water",
    "Depth",
    "Area",
    "Top",
    "Time",
    "Trails",
    "Quality",
    "Fairway type",
    "Fairway depth",
    "Minimum depth",
    "Comparison",
    "Fairway state",
    "Mark",
    "Depth min",
    "Depth max"
  )

  object Finnish extends Lang(
    "Liikennevirasto",
    "Puolustusvoimat",
    "Helsingin Satama",
    "Helsingin kaupunki",
    "Espoon kaupunki",
    "Nimi",
    "Sijainti",
    "Tyyppi",
    "Navigointi",
    "Rakenne",
    "Nopeus",
    "Vesi",
    "Syvyys",
    "Vaikutusalue",
    "Huippu",
    "Kesto",
    "Urat",
    "Laatuluokka",
    "Väyläalueen tyyppi",
    "Väyläalueen syvyys",
    "Haraussyvyys",
    "Vertaustaso",
    "Väylän tila",
    "Merkin laji",
    "Syvyys min",
    "Syvyys max"
  )

  object Swedish extends Lang(
    "Trafikverket",
    "Försvarsmakten",
    "Helsingfors Hamn",
    "Helsingfors stad",
    "Esbo stad",
    "Namn",
    "Plats",
    "Typ",
    "Navigering",
    "Struktur",
    "Hastighet",
    "Vatten",
    "Djup",
    "Område",
    "Max",
    "Tid",
    "Spår",
    "Kvalitet",
    "Farledstyp",
    "Farledens djup",
    "Ramat djup",
    "Jämförelse",
    "Farledens status",
    "Märke",
    "Djup min",
    "Djup max"
  )

}
