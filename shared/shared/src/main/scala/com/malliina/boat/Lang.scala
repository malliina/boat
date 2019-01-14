package com.malliina.boat

trait ShipTypesLang {
  val wingInGround: String
  val fishing: String
  val tug: String
  val dredger: String
  val diveVessel: String
  val militaryOps: String
  val sailing: String
  val pleasureCraft: String
  val highSpeedCraft: String
  val pilotVessel: String
  val searchAndRescue: String
  val searchAndRescueAircraft: String
  val portTender: String
  val antiPollution: String
  val lawEnforce: String
  val localVessel: String
  val medicalTransport: String
  val specialCraft: String
  val passenger: String
  val cargo: String
  val tanker: String
  val other: String
  val unknown: String
}

object ShipTypesLang {

  object Fi extends ShipTypesLang {
    val wingInGround = "Wing in Ground"
    val fishing = "Kalastus"
    val tug = "Hinaus"
    val dredger = "Ruoppaaja"
    val diveVessel = "Sukellus"
    val militaryOps = "Asevoimat"
    val sailing = "Purjehdus"
    val pleasureCraft = "Huvialus"
    val highSpeedCraft = "Vauhtivene"
    val pilotVessel = "Pilot"
    val searchAndRescue = "Search and Rescue"
    val searchAndRescueAircraft = "Search and Rescue Aircraft"
    val portTender = "Satama"
    val antiPollution = "Anti-Pollution"
    val lawEnforce = "Patrol Vessel"
    val localVessel = "Local Vessel"
    val medicalTransport = "Hospital Ship"
    val specialCraft = "Special Craft"
    val passenger = "Matkustaja"
    val cargo = "Rahtilaiva"
    val tanker = "Tankkeri"
    val other = "Muu"
    val unknown = "Tuntematon"
  }

  object Se extends ShipTypesLang {
    val wingInGround = "Wing in Ground"
    val fishing = "Fiske"
    val tug = "Bogserbåt"
    val dredger = "Mudderverk"
    val diveVessel = "Dykning"
    val militaryOps = "Militär"
    val sailing = "Segling"
    val pleasureCraft = "Nöje"
    val highSpeedCraft = "Fartbåt"
    val pilotVessel = "Pilot"
    val searchAndRescue = "Search and Rescue"
    val searchAndRescueAircraft = "Search and Rescue Aircraft"
    val portTender = "Hamn"
    val antiPollution = "Anti-Pollution"
    val lawEnforce = "Patrullering"
    val localVessel = "Lokal"
    val medicalTransport = "Sjukhus"
    val specialCraft = "Special"
    val passenger = "Passagerar"
    val cargo = "Frakt"
    val tanker = "Tanker"
    val other = "Annan"
    val unknown = "Okänd"
  }

  object En extends ShipTypesLang {
    val wingInGround = "Wing in Ground"
    val fishing = "Fishing"
    val tug = "Tug"
    val dredger = "Dredger"
    val diveVessel = "Diving Support Vessel"
    val militaryOps = "Military Ops"
    val sailing = "Sailing Vessel"
    val pleasureCraft = "Pleasure Craft"
    val highSpeedCraft = "High-Speed Craft"
    val pilotVessel = "Pilot Vessel"
    val searchAndRescue = "Search and Rescue"
    val searchAndRescueAircraft = "Search and Rescue Aircraft"
    val portTender = "Port Tender"
    val antiPollution = "Anti-Pollution"
    val lawEnforce = "Patrol Vessel"
    val localVessel = "Local Vessel"
    val medicalTransport = "Hospital Ship"
    val specialCraft = "Special Craft"
    val passenger = "Passenger"
    val cargo = "Cargo"
    val tanker = "Tanker"
    val other = "Other"
    val unknown = "Unknown"
  }

}

sealed trait Lang {
  val transportAgency: String
  val defenceForces: String
  val portOfHelsinki: String
  val cityOfHelsinki: String
  val cityOfEspoo: String
  val name: String
  val location: String
  val `type`: String
  val navigation: String
  val construction: String
  val speed: String
  val water: String
  val depth: String
  val influence: String
  val top: String
  val duration: String
  val tracks: String
  val qualityClass: String
  val fairwayType: String
  val fairwayDepth: String
  val harrowDepth: String
  val comparisonLevel: String
  val state: String
  val markType: String
  val minDepth: String
  val maxDepth: String
  val draft: String
  val destination: String
  val time: String
  val shipType: String
  val shipTypes: ShipTypesLang
}

object Lang {
  val default = Finnish

  def apply(language: Language): Lang = language match {
    case Language.swedish => Swedish
    case Language.finnish => Finnish
    case Language.english => English
    case _ => default
  }

  object English extends Lang {
    val transportAgency = "Finnish Transport Agency"
    val defenceForces = "Defence forces"
    val portOfHelsinki = "Port of Helsinki"
    val cityOfHelsinki = "City of Helsinki"
    val cityOfEspoo = "City of Espoo"
    val name = "Name"
    val `type` = "Type"
    val location = "Location"
    val navigation = "Navigation"
    val construction = "Structure"
    val speed = "Speed"
    val water = "Water"
    val depth = "Depth"
    val influence = "Area"
    val top = "Top"
    val duration = "Time"
    val tracks = "Trails"
    val qualityClass = "Quality"
    val fairwayType = "Fairway type"
    val fairwayDepth = "Fairway depth"
    val harrowDepth = "Minimum depth"
    val comparisonLevel = "Comparison"
    val state = "Fairway state"
    val markType = "Mark"
    val minDepth = "Depth min"
    val maxDepth = "Depth max"
    val draft = "Draft"
    val destination = "Destination"
    val shipType = "Ship type"
    val time = "Time"
    val shipTypes = ShipTypesLang.En
  }

  object Finnish extends Lang {
    val transportAgency = "Liikennevirasto"
    val defenceForces = "Puolustusvoimat"
    val portOfHelsinki = "Helsingin Satama"
    val cityOfHelsinki = "Helsingin kaupunki"
    val cityOfEspoo = "Espoon kaupunki"
    val name = "Nimi"
    val location = "Sijainti"
    val `type` = "Tyyppi"
    val navigation = "Navigointi"
    val construction = "Rakenne"
    val speed = "Nopeus"
    val water = "Vesi"
    val depth = "Syvyys"
    val influence = "Vaikutusalue"
    val top = "Huippu"
    val duration = "Kesto"
    val tracks = "Urat"
    val qualityClass = "Laatuluokka"
    val fairwayType = "Väyläalueen laji"
    val fairwayDepth = "Väyläalueen syvyys"
    val harrowDepth = "Haraussyvyys"
    val comparisonLevel = "Vertaustaso"
    val state = "Väylän tila"
    val markType = "Merkin laji"
    val minDepth = "Syvyys min"
    val maxDepth = "Syvyys max"
    val draft = "Syväys"
    val destination = "Määränpää"
    val time = "Aika"
    val shipType = "Alus"
    val shipTypes = ShipTypesLang.Fi
  }

  object Swedish extends Lang {
    val transportAgency = "Trafikverket"
    val defenceForces = "Försvarsmakten"
    val portOfHelsinki = "Helsingfors Hamn"
    val cityOfHelsinki = "Helsingfors stad"
    val cityOfEspoo = "Esbo stad"
    val name = "Namn"
    val location = "Plats"
    val `type` = "Typ"
    val navigation = "Navigering"
    val construction = "Struktur"
    val speed = "Hastighet"
    val water = "Vatten"
    val depth = "Djup"
    val influence = "Område"
    val top = "Max"
    val duration = "Tid"
    val tracks = "Spår"
    val qualityClass = "Kvalitet"
    val fairwayType = "Farledstyp"
    val fairwayDepth = "Farledens djup"
    val harrowDepth = "Ramat djup"
    val comparisonLevel = "Jämförelse"
    val state = "Farledens status"
    val markType = "Märke"
    val minDepth = "Djup min"
    val maxDepth = "Djup max"
    val draft = "Djupgående"
    val destination = "Destination"
    val time = "Tid"
    val shipType = "Fartyg"
    val shipTypes = ShipTypesLang.Se
  }

}
