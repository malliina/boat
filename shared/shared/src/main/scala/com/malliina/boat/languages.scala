package com.malliina.boat

import play.api.libs.json.Json

case class FairwayStateLang(
    confirmed: String,
    aihio: String,
    mayChange: String,
    changeAihio: String,
    mayBeRemoved: String,
    removed: String
)

object FairwayStateLang {
  implicit val json = Json.format[FairwayStateLang]
}

case class MarkTypeLang(unknown: String, lateral: String, cardinal: String)

object MarkTypeLang {
  implicit val json = Json.format[MarkTypeLang]
}

case class ZonesLang(area: String, fairway: String, areaAndFairway: String)

object ZonesLang {
  implicit val json = Json.format[ZonesLang]
}

case class FlotationLang(floating: String, solid: String, other: String)

object FloationLang {
  implicit val json = Json.format[FlotationLang]

  val fi = FlotationLang("Kelluva", "Kiinteä", "Muu")
  val se = FlotationLang("Flytande", "Fast", "Annan")
  val en = FlotationLang("Floating", "Solid", "Other")
}

case class AidTypeLang(
    unknown: String,
    lighthouse: String,
    sectorLight: String,
    leadingMark: String,
    directionalLight: String,
    minorLight: String,
    otherMark: String,
    edgeMark: String,
    radarTarget: String,
    buoy: String,
    beacon: String,
    signatureLighthouse: String,
    cairn: String
)

object AidTypeLang {
  implicit val json = Json.format[AidTypeLang]
  val fi = AidTypeLang(
    "Tuntematon",
    "Merimajakka",
    "Sektoriloisto",
    "Linjamerkki",
    "Suuntaloisto",
    "Apuloisto",
    "Muu merkki",
    "Reunamerkki",
    "Tutkamerkki",
    "Poiju",
    "Viitta",
    "Tunnusmajakka",
    "Kummeli"
  )
  val se = AidTypeLang(
    "Okänd",
    "Havsfyr",
    "Sektorfyr",
    "Ensmärke",
    "Riktning",
    "Hjälpfyr",
    "Annat märke",
    "Randmärke",
    "Radarmärke",
    "Boj",
    "Prick",
    "Båk",
    "Kummel"
  )
  val en = AidTypeLang(
    "Unknown",
    "Lighthouse",
    "Sector light",
    "Leading mark",
    "Directional light",
    "Minor light",
    "Other mark",
    "Edge mark",
    "Radar target",
    "Buoy",
    "Beacon",
    "Signature lighthouse",
    "Cairn"
  )
}

case class NavMarkLang(
    unknown: String,
    left: String,
    right: String,
    north: String,
    south: String,
    west: String,
    east: String,
    rock: String,
    safeWaters: String,
    special: String,
    notApplicable: String
)

object NavMarkLang {
  implicit val json = Json.format[NavMarkLang]
  val fi = NavMarkLang(
    "Tuntematon",
    "Vasen",
    "Oikea",
    "Pohjois",
    "Etelä",
    "Länsi",
    "Itä",
    "Karimerkki",
    "Turvavesimerkki",
    "Erikoismerkki",
    "Ei sovellettavissa"
  )
  val se = NavMarkLang(
    "Okänd",
    "Vänster",
    "Höger",
    "Nord",
    "Söder",
    "Väster",
    "Ost",
    "Grund",
    "Mittledsmärke",
    "Specialmärke",
    "Inte tillämpbar"
  )
  val en = NavMarkLang(
    "Unknown",
    "Left",
    "Right",
    "North",
    "South",
    "West",
    "East",
    "Rocks",
    "Safe water",
    "Special mark",
    "Not applicable"
  )
}

case class ConstructionLang(
    buoyBeacon: String,
    iceBuoy: String,
    beaconBuoy: String,
    superBeacon: String,
    exteriorLight: String,
    dayBoard: String,
    helicopterPlatform: String,
    radioMast: String,
    waterTower: String,
    smokePipe: String,
    radarTower: String,
    churchTower: String,
    superBuoy: String,
    edgeCairn: String,
    compassCheck: String,
    borderMark: String,
    borderLineMark: String,
    channelEdgeLight: String,
    tower: String
)

object ConstructionLang {
  implicit val json = Json.format[ConstructionLang]
  val fi = ConstructionLang(
    "Poijuviitta",
    "Jääpoiju",
    "Viittapoiju",
    "Suurviitta",
    "Fasadivalo",
    "Levykummeli",
    "Helikopteritasanne",
    "Radiomasto",
    "Vesitorni",
    "Savupiippu",
    "Tutkatorni",
    "Kirkontorni",
    "Suurpoiju",
    "Reunakummeli",
    "Kompassintarkistuspaikka",
    "Rajamerkki",
    "Rajalinjamerkki",
    "Kanavan reunavalo",
    "Torni"
  )
  val se = ConstructionLang(
    "Bojprick",
    "Isboj",
    "Prickboj",
    "Storprick",
    "Ljus",
    "Panelkummel",
    "Helikopterplatform",
    "Radiomast",
    "Vattentorn",
    "Skorsten",
    "Radartorn",
    "Kyrkotorn",
    "Storboj",
    "Randkummel",
    "Kompassplats",
    "Gränsmärke",
    "Gränslinjemärke",
    "Kanalens randljus",
    "Torn"
  )
  val en = ConstructionLang(
    "Buoy beacon",
    "Ice buoy",
    "Beacon buoy",
    "Super beacon",
    "Exterior light",
    "Dayboard",
    "Helicopter platform",
    "Radio mast",
    "Water tower",
    "Chimney",
    "Radar tower",
    "Church tower",
    "Super buoy",
    "Edge cairn",
    "Compass check",
    "Border mark",
    "Border line mark",
    "Channel edge light",
    "Tower"
  )
}

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

  val fi = ShipTypesLang(
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

  val se = ShipTypesLang(
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

  val en = ShipTypesLang(
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

case class FairwayTypeLang(
    navigation: String,
    anchoring: String,
    meetup: String,
    harborPool: String,
    turn: String,
    channel: String,
    coastTraffic: String,
    core: String,
    special: String,
    lock: String,
    confirmedExtra: String,
    helcom: String,
    pilot: String
)

object FairwayTypeLang {
  implicit val json = Json.format[FairwayTypeLang]
}

case class FairwayLang(fairwayType: String,
                       fairwayDepth: String,
                       harrowDepth: String,
                       minDepth: String,
                       maxDepth: String,
                       state: String,
                       states: FairwayStateLang,
                       zones: ZonesLang,
                       types: FairwayTypeLang)

object FairwayLang {
  implicit val json = Json.format[FairwayLang]
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
                    owner: String,
                    types: MarkTypeLang,
                    navTypes: NavMarkLang,
                    structures: ConstructionLang,
                    aidTypes: AidTypeLang)

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
    fairway: FairwayLang,
    track: TrackLang,
    mark: MarkLang,
    ais: AisLang,
    shipTypes: ShipTypesLang
)

object Lang {
  implicit val json = Json.format[Lang]

  def apply(language: Language): Lang = language match {
    case Language.swedish => se
    case Language.finnish => fi
    case Language.english => en
    case _                => default
  }

  val en = Lang(
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
    fairway = FairwayLang(
      fairwayType = "Fairway type",
      fairwayDepth = "Fairway depth",
      harrowDepth = "Minimum depth",
      minDepth = "Depth min",
      maxDepth = "Depth max",
      state = "Fairway state",
      states = FairwayStateLang(
        "Confirmed",
        "Unfinished",
        "Changing",
        "Change",
        "May be removed",
        "Removed"
      ),
      zones = ZonesLang("Area", "Fairway", "Area and fairway"),
      types = FairwayTypeLang(
        "Navigation",
        "Anchoring area",
        "Overtaking and meetup",
        "Harbor pool",
        "Turn area",
        "Channel",
        "Coast traffic",
        "Main fairway",
        "Special area",
        "Lock",
        "Confirmed area",
        "HELCOM area",
        "Pilot boarding place"
      )
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
      owner = "Owner",
      types = MarkTypeLang("Unknown", "Lateral", "Cardinal"),
      navTypes = NavMarkLang.en,
      structures = ConstructionLang.en,
      aidTypes = AidTypeLang.en
    ),
    ais = AisLang(
      draft = "Draft",
      destination = "Destination",
      shipType = "Ship type"
    ),
    shipTypes = ShipTypesLang.en
  )

  val fi = Lang(
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
    fairway = FairwayLang(
      fairwayType = "Väyläalueen laji",
      fairwayDepth = "Väyläalueen syvyys",
      harrowDepth = "Haraussyvyys",
      minDepth = "Syvyys min",
      maxDepth = "Syvyys max",
      state = "Väylän tila",
      states = FairwayStateLang(
        "Vahvistettu",
        "Aihio",
        "Muutoksen alainen",
        "Muutosaihio",
        "Poiston alainen",
        "Poistettu"
      ),
      zones = ZonesLang("Alue", "Väylä", "Alue ja väylä"),
      types = FairwayTypeLang(
        "Navigointialue",
        "Ankkurointialue",
        "Ohitus- ja kohtaamisalue",
        "Satama-allas",
        "Kääntöallas",
        "Kanava",
        "Rannikkoliikenteen alue",
        "Runkoväylä",
        "Erikoisalue",
        "Sulku",
        "Varmistettu lisäalue",
        "HELCOM-alue",
        "Luotsin otto- ja jättöalue"
      )
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
      owner = "Omistaja",
      types = MarkTypeLang("Tuntematon", "Lateraali", "Kardinaali"),
      navTypes = NavMarkLang.fi,
      structures = ConstructionLang.fi,
      aidTypes = AidTypeLang.fi
    ),
    ais = AisLang(
      draft = "Syväys",
      destination = "Määränpää",
      shipType = "Alus"
    ),
    shipTypes = ShipTypesLang.fi
  )

  val se = Lang(
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
    fairway = FairwayLang(
      fairwayType = "Farledstyp",
      fairwayDepth = "Farledens djup",
      harrowDepth = "Ramat djup",
      minDepth = "Djup min",
      maxDepth = "Djup max",
      state = "Farledens status",
      states = FairwayStateLang(
        "Bekräftat",
        "Pågående",
        "Ändras",
        "Ändring",
        "Tags bort",
        "Borttagen"
      ),
      zones = ZonesLang("Område", "Farled", "Område och farled"),
      types = FairwayTypeLang(
        "Navigeringsområde",
        "Ankringsområde",
        "Mötespunkt",
        "Hamnbassäng",
        "Vändområde",
        "Kanal",
        "Kusttrafik",
        "Huvudled",
        "Specialområde",
        "Sluss",
        "Försäkrat område",
        "HELCOM-område",
        "Plats där lots möter"
      )
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
      owner = "Ägare",
      types = MarkTypeLang("Okänd", "Lateral", "Kardinal"),
      navTypes = NavMarkLang.se,
      structures = ConstructionLang.se,
      aidTypes = AidTypeLang.se
    ),
    ais = AisLang(
      draft = "Djupgående",
      destination = "Destination",
      shipType = "Fartyg"
    ),
    shipTypes = ShipTypesLang.se
  )
  val default = fi
}
