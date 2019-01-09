package com.malliina.boat

trait ShipTypesLang {
  def wingInGround: String

  def fishing: String

  def tug: String

  def dredger: String

  def diveVessel: String

  def militaryOps: String

  def sailing: String

  def pleasureCraft: String

  def highSpeedCraft: String

  def pilotVessel: String

  def searchAndRescue: String

  def portTender: String

  def antiPollution: String

  def lawEnforce: String

  def localVessel: String

  def medicalTransport: String

  def specialCraft: String

  def passenger: String

  def cargo: String

  def tanker: String

  def other: String

  def unknown: String
}

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
                           val maxDepth: String,
                           val draft: String,
                           val destination: String,
                           val shipType: String,
                           val wingInGround: String,
                           val fishing: String,
                           val tug: String,
                           val dredger: String,
                           val diveVessel: String,
                           val militaryOps: String,
                           val sailing: String,
                           val pleasureCraft: String,
                           val highSpeedCraft: String,
                           val pilotVessel: String,
                           val searchAndRescue: String,
                           val portTender: String,
                           val antiPollution: String,
                           val lawEnforce: String,
                           val localVessel: String,
                           val medicalTransport: String,
                           val specialCraft: String,
                           val passenger: String,
                           val cargo: String,
                           val tanker: String,
                           val other: String,
                           val unknown: String)

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
    "Depth max",
    "Draft",
    "Destination",
    "Ship type",
    "Wing in Ground",
    "Fishing",
    "Tug",
    "Dredger",
    "Diving Support Vessel",
    "Military Ops",
    "Sailing Vessel",
    "Pleasure Craft",
    "High-Speed Craft",
    "Pilot Vessel",
    "Search and Rescue",
    "Port Tender",
    "Anti-Pollution",
    "Patrol Vessel",
    "Local Vessel",
    "Hospital Ship",
    "Special Craft",
    "Passenger",
    "Cargo",
    "Tanker",
    "Other",
    "Unknown"
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
    "Syvyys max",
    "Syväys",
    "Määränpää",
    "Alus",
    "Wing in Ground",
    "Kalastus",
    "Hinaus",
    "Ruoppaaja",
    "Sukellus",
    "Asevoimat",
    "Purjehdus",
    "Huvialus",
    "Vauhtivene",
    "Pilot",
    "Search and Rescue",
    "Satama",
    "Anti-Pollution",
    "Patrol Vessel",
    "Local Vessel",
    "Hospital Ship",
    "Special Craft",
    "Matkustaja",
    "Rahtilaiva",
    "Tankkeri",
    "Muu",
    "Tuntematon"
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
    "Djup max",
    "Djupgående",
    "Destination",
    "Fartyg",
    "Wing in Ground",
    "Fiske",
    "Bogserbåt",
    "Mudderverk",
    "Dykning",
    "Militär",
    "Segling",
    "Nöje",
    "Fartbåt",
    "Pilot",
    "Search and Rescue",
    "Hamn",
    "Anti-Pollution",
    "Patrullering",
    "Lokal",
    "Sjukhus",
    "Special",
    "Passagerar",
    "Frakt",
    "Tanker",
    "Annan",
    "Okänd"
  )

}
