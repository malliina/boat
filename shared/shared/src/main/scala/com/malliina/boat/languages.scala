package com.malliina.boat

import play.api.libs.json.Json

case class SpecialCategory(fishing: String,
                           tug: String,
                           dredger: String,
                           diveVessel: String,
                           militaryOps: String,
                           sailing: String,
                           pleasureCraft: String)

object SpecialCategory {
  implicit val json = Json.format[SpecialCategory]
}

case class ShipTypesLang(
    wingInGround: String,
    special: SpecialCategory,
    highSpeedCraft: String,
    pilotVessel: String,
    searchAndRescue: String,
    searchAndRescueAircraft: String,
    portTender: String,
    antiPollution: String,
    lawEnforce: String,
    localVessel: String,
    medicalTransport: String,
    specialCraft: String,
    passenger: String,
    cargo: String,
    tanker: String,
    other: String,
    unknown: String
)

object ShipTypesLang {
  implicit val json = Json.format[ShipTypesLang]

  val Fi = ShipTypesLang(
    wingInGround = "Wing in Ground",
    special = SpecialCategory(
      fishing = "Kalastus",
      tug = "Hinaus",
      dredger = "Ruoppaaja",
      diveVessel = "Sukellus",
      militaryOps = "Asevoimat",
      sailing = "Purjehdus",
      pleasureCraft = "Huvialus"
    ),
    highSpeedCraft = "Vauhtivene",
    pilotVessel = "Pilot",
    searchAndRescue = "Search and Rescue",
    searchAndRescueAircraft = "Search and Rescue Aircraft",
    portTender = "Satama",
    antiPollution = "Anti-Pollution",
    lawEnforce = "Patrol Vessel",
    localVessel = "Local Vessel",
    medicalTransport = "Hospital Ship",
    specialCraft = "Special Craft",
    passenger = "Matkustaja",
    cargo = "Rahtilaiva",
    tanker = "Tankkeri",
    other = "Muu",
    unknown = "Tuntematon"
  )

  val Se = ShipTypesLang(
    wingInGround = "Wing in Ground",
    special = SpecialCategory(
      fishing = "Fiske",
      tug = "Bogserbåt",
      dredger = "Mudderverk",
      diveVessel = "Dykning",
      militaryOps = "Militär",
      sailing = "Segling",
      pleasureCraft = "Nöje"
    ),
    highSpeedCraft = "Fartbåt",
    pilotVessel = "Pilot",
    searchAndRescue = "Search and Rescue",
    searchAndRescueAircraft = "Search and Rescue Aircraft",
    portTender = "Hamn",
    antiPollution = "Anti-Pollution",
    lawEnforce = "Patrullering",
    localVessel = "Lokal",
    medicalTransport = "Sjukhus",
    specialCraft = "Special",
    passenger = "Passagerar",
    cargo = "Frakt",
    tanker = "Tanker",
    other = "Annan",
    unknown = "Okänd"
  )

  val En = ShipTypesLang(
    wingInGround = "Wing in Ground",
    special = SpecialCategory(
      fishing = "Fishing",
      tug = "Tug",
      dredger = "Dredger",
      diveVessel = "Diving Support Vessel",
      militaryOps = "Military Ops",
      sailing = "Sailing Vessel",
      pleasureCraft = "Pleasure Craft"
    ),
    highSpeedCraft = "High-Speed Craft",
    pilotVessel = "Pilot Vessel",
    searchAndRescue = "Search and Rescue",
    searchAndRescueAircraft = "Search and Rescue Aircraft",
    portTender = "Port Tender",
    antiPollution = "Anti-Pollution",
    lawEnforce = "Patrol Vessel",
    localVessel = "Local Vessel",
    medicalTransport = "Hospital Ship",
    specialCraft = "Special Craft",
    passenger = "Passenger",
    cargo = "Cargo",
    tanker = "Tanker",
    other = "Other",
    unknown = "Unknown"
  )
}

case class Fairway(fairwayType: String,
                   fairwayDepth: String,
                   harrowDepth: String,
                   minDepth: String,
                   maxDepth: String,
                   state: String)

object Fairway {
  implicit val json = Json.format[Fairway]
}

case class AisLang(draft: String, destination: String, shipType: String)

object AisLang {
  implicit val json = Json.format[AisLang]
}

case class TrackLang(tracks: String,
                     speed: String,
                     water: String,
                     depth: String,
                     top: String,
                     duration: String)

object TrackLang {
  implicit val json = Json.format[TrackLang]
}

case class MarkLang(markType: String,
                    aidType: String,
                    navigation: String,
                    construction: String,
                    influence: String,
                    location: String,
                    owner: String)

object MarkLang {
  implicit val json = Json.format[MarkLang]
}

case class SpecialWords(transportAgency: String,
                        defenceForces: String,
                        portOfHelsinki: String,
                        cityOfHelsinki: String,
                        cityOfEspoo: String)

object SpecialWords {
  implicit val json = Json.format[SpecialWords]
}

case class Lang(
    name: String,
    qualityClass: String,
    time: String,
    comparisonLevel: String,
    specialWords: SpecialWords,
    fairway: Fairway,
    track: TrackLang,
    mark: MarkLang,
    ais: AisLang,
    shipTypes: ShipTypesLang
)

object Lang {
  implicit val json = Json.format[Lang]

  def apply(language: Language): Lang = language match {
    case Language.swedish => Swedish
    case Language.finnish => Finnish
    case Language.english => English
    case _                => default
  }

  val English = Lang(
    name = "Name",
    qualityClass = "Quality",
    time = "Time",
    comparisonLevel = "Comparison",
    specialWords = SpecialWords(
      transportAgency = "Finnish Transport Agency",
      defenceForces = "Defence forces",
      portOfHelsinki = "Port of Helsinki",
      cityOfHelsinki = "City of Helsinki",
      cityOfEspoo = "City of Espoo"
    ),
    fairway = Fairway(
      fairwayType = "Fairway type",
      fairwayDepth = "Fairway depth",
      harrowDepth = "Minimum depth",
      minDepth = "Depth min",
      maxDepth = "Depth max",
      state = "Fairway state"
    ),
    track = TrackLang(
      tracks = "Trails",
      speed = "Speed",
      water = "Water",
      depth = "Depth",
      top = "Top",
      duration = "Time"
    ),
    mark = MarkLang(
      markType = "Mark",
      aidType = "Type",
      navigation = "Navigation",
      construction = "Structure",
      influence = "Area",
      location = "Location",
      owner = "Owner"
    ),
    ais = AisLang(
      draft = "Draft",
      destination = "Destination",
      shipType = "Ship type"
    ),
    shipTypes = ShipTypesLang.En
  )

  val Finnish = Lang(
    name = "Nimi",
    qualityClass = "Laatuluokka",
    time = "Aika",
    comparisonLevel = "Vertaustaso",
    specialWords = SpecialWords(
      transportAgency = "Liikennevirasto",
      defenceForces = "Puolustusvoimat",
      portOfHelsinki = "Helsingin Satama",
      cityOfHelsinki = "Helsingin kaupunki",
      cityOfEspoo = "Espoon kaupunki"
    ),
    fairway = Fairway(
      fairwayType = "Väyläalueen laji",
      fairwayDepth = "Väyläalueen syvyys",
      harrowDepth = "Haraussyvyys",
      minDepth = "Syvyys min",
      maxDepth = "Syvyys max",
      state = "Väylän tila"
    ),
    track = TrackLang(
      tracks = "Urat",
      speed = "Nopeus",
      water = "Vesi",
      depth = "Syvyys",
      top = "Huippu",
      duration = "Kesto"
    ),
    mark = MarkLang(
      markType = "Merkin laji",
      aidType = "Tyyppi",
      navigation = "Navigointi",
      construction = "Rakenne",
      influence = "Vaikutusalue",
      location = "Sijainti",
      owner = "Omistaja"
    ),
    ais = AisLang(
      draft = "Syväys",
      destination = "Määränpää",
      shipType = "Alus"
    ),
    shipTypes = ShipTypesLang.Fi
  )

  val Swedish = Lang(
    name = "Namn",
    qualityClass = "Kvalitet",
    time = "Tid",
    comparisonLevel = "Jämförelse",
    specialWords = SpecialWords(
      transportAgency = "Trafikverket",
      defenceForces = "Försvarsmakten",
      portOfHelsinki = "Helsingfors Hamn",
      cityOfHelsinki = "Helsingfors stad",
      cityOfEspoo = "Esbo stad"
    ),
    fairway = Fairway(
      fairwayType = "Farledstyp",
      fairwayDepth = "Farledens djup",
      harrowDepth = "Ramat djup",
      minDepth = "Djup min",
      maxDepth = "Djup max",
      state = "Farledens status"
    ),
    track = TrackLang(
      tracks = "Spår",
      speed = "Hastighet",
      water = "Vatten",
      depth = "Djup",
      top = "Max",
      duration = "Tid"
    ),
    mark = MarkLang(
      markType = "Märke",
      aidType = "Typ",
      navigation = "Navigering",
      construction = "Struktur",
      influence = "Område",
      location = "Plats",
      owner = "Ägare"
    ),
    ais = AisLang(
      draft = "Djupgående",
      destination = "Destination",
      shipType = "Fartyg"
    ),
    shipTypes = ShipTypesLang.Se
  )
  val default = Finnish
}
