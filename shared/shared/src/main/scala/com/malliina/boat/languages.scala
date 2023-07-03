package com.malliina.boat

import com.malliina.http.FullUrl
import com.malliina.values.Email
import io.circe.*
import io.circe.generic.semiauto.*

case class TrafficSignTypeLang(
  unknown: String,
  forbidden: String,
  limit: String,
  info: String,
  cableOrWire: String,
  direction: String,
  lightInfo: String
) derives Codec.AsObject

case class TrafficSignLimitsLang(
  unknown: String,
  noAnchoring: String,
  noParking: String,
  noAttachment: String,
  noOvertaking: String,
  noRendezVous: String,
  noWaves: String,
  noWaterSkiing: String,
  noWindSurfing: String,
  noMotorPower: String,
  noJetSkiing: String,
  speedLimit: String,
  stopSign: String,
  generalWarning: String,
  signalMandatory: String,
  heightLimit: String,
  depthLimit: String,
  widthLimit: String
) derives Codec.AsObject

object TrafficSignLimitsLang:
  def apply(
    unknown: String,
    noWaterSkiing: String,
    stopSign: String,
    generalWarning: String,
    signalMandatory: String,
    heightLimit: String,
    depthLimit: String,
    widthLimit: String,
    limits: LimitTypes
  ): TrafficSignLimitsLang = TrafficSignLimitsLang(
    unknown,
    limits.noAnchoring,
    limits.noStopping,
    limits.noAttachment,
    limits.noOvertaking,
    limits.noRendezVous,
    limits.noWaves,
    noWaterSkiing,
    limits.noWindSurfing,
    limits.noMotorPower,
    limits.noJetSkiing,
    limits.speedLimit,
    stopSign,
    generalWarning,
    signalMandatory,
    heightLimit,
    depthLimit,
    widthLimit
  )

case class TrafficSignInfoLang(
  strongCurrent: String,
  fairwaySide: String,
  swimmingWarning: String,
  useRadio: String,
  parkingAllowed: String,
  attachmentAllowed: String,
  airCable: String,
  phone: String,
  cableFerryCrossing: String,
  ferryCrossing: String,
  radioPossibility: String,
  drinkingPoint: String,
  limitEnds: String,
  cableSign: String,
  wireSign: String,
  directionUpper: String,
  directionLower: String
) derives Codec.AsObject

case class TrafficSignLang(
  limits: TrafficSignLimitsLang,
  infos: TrafficSignInfoLang,
  types: TrafficSignTypeLang
) derives Codec.AsObject

case class FairwayStateLang(
  confirmed: String,
  aihio: String,
  mayChange: String,
  changeAihio: String,
  mayBeRemoved: String,
  removed: String
) derives Codec.AsObject

case class MarkTypeLang(unknown: String, lateral: String, cardinal: String) derives Codec.AsObject

case class ZonesLang(area: String, fairway: String, areaAndFairway: String) derives Codec.AsObject

case class FlotationLang(floating: String, solid: String, other: String) derives Codec.AsObject

object FlotationLang:
  val fi = FlotationLang("Kelluva", "Kiinteä", "Muu")
  val se = FlotationLang("Flytande", "Fast", "Annan")
  val en = FlotationLang("Floating", "Solid", "Other")

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
) derives Codec.AsObject

object AidTypeLang:
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
) derives Codec.AsObject

object NavMarkLang:
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
) derives Codec.AsObject

object ConstructionLang:
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

case class SpecialCategory(
  fishing: String,
  tug: String,
  dredger: String,
  diveVessel: String,
  militaryOps: String,
  sailing: String,
  pleasureCraft: String
) derives Codec.AsObject

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
) derives Codec.AsObject

object ShipTypesLang:
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
) derives Codec.AsObject

case class FairwayLang(
  fairwayType: String,
  fairwayDepth: String,
  harrowDepth: String,
  minDepth: String,
  maxDepth: String,
  state: String,
  states: FairwayStateLang,
  zones: ZonesLang,
  types: FairwayTypeLang
) derives Codec.AsObject

case class AisLang(draft: String, destination: String, shipType: String) derives Codec.AsObject

case class EnvLang(altitude: String) derives Codec.AsObject

case class TrackLang(
  track: String,
  boats: String,
  tracks: String,
  days: String,
  routes: String,
  speed: String,
  water: String,
  depth: String,
  top: String,
  duration: String,
  hours: String,
  distance: String,
  topSpeed: String,
  avgSpeed: String,
  temperature: String,
  waterTemp: String,
  date: String,
  trackHistory: String,
  graph: String,
  coordinate: String,
  comments: String,
  env: EnvLang
) derives Codec.AsObject

case class MarkLang(
  markType: String,
  aidType: String,
  navigation: String,
  construction: String,
  influence: String,
  location: String,
  owner: String,
  types: MarkTypeLang,
  navTypes: NavMarkLang,
  structures: ConstructionLang,
  aidTypes: AidTypeLang,
  flotation: FlotationLang,
  lit: String,
  yes: String,
  no: String,
  extraInfo1: String,
  extraInfo2: String
) derives Codec.AsObject

case class SpecialWords(
  transportAgency: String,
  transportInfrastructureAgency: String,
  defenceForces: String,
  portOfHelsinki: String,
  cityOfHelsinki: String,
  cityOfEspoo: String
) derives Codec.AsObject

case class TextLink(text: String, url: FullUrl) derives Codec.AsObject

object TextLink:
  def url(link: FullUrl) = TextLink(link.url, link)

case class Attribution(title: String, text: Option[String], links: Seq[TextLink])
  derives Codec.AsObject

object Attribution:
  def url(title: String, link: FullUrl) = Attribution(title, None, Seq(TextLink.url(link)))

case class AttributionInfo(title: String, attributions: Seq[Attribution]) derives Codec.AsObject

object AttributionInfo:
  val fi = translated(
    "Lisenssit",
    "Merikartta-aineistot",
    "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia."
  )
  val se = translated(
    "Licenser",
    "Sjökortsmaterial",
    "Källa: Trafikverket. Får inte användas för navigationsändamål. Uppfyller inte fordringarna för officiella sjökort."
  )
  val en = translated(
    "Attributions",
    "Nautical charts",
    "Source: Finnish Transport Agency. Not for navigational use. Does not meet the requirements for official nautical charts."
  )

  private def translated(title: String, maritimeData: String, chartsLicense: String) =
    AttributionInfo(
      title,
      Seq(
        Attribution(
          maritimeData,
          Option(chartsLicense),
          Seq(TextLink("CC 4.0", FullUrl.https("creativecommons.org", "/licenses/by/4.0/")))
        ),
        Attribution.url("Font Awesome", FullUrl.https("fontawesome.com", "/license")),
        Attribution.url("Inspiration", FullUrl.https("github.com", "/iaue/poiju.io"))
      )
    )

case class ProfileLang(
  username: String,
  signedInAs: String,
  logout: String,
  chooseIdentityProvider: String,
  language: String,
  chooseLanguage: String,
  finnish: String,
  swedish: String,
  english: String,
  deleteAccount: String,
  deleteAccountConfirmation: String
) derives Codec.AsObject

case class MessagesLang(
  loading: String,
  failedToLoadProfile: String,
  noSavedTracks: String,
  notAvailable: String
) derives Codec.AsObject

case class FormatsLang(date: String, time: String, timeShort: String, dateTime: String)
  derives Codec.AsObject

case class BoatLang(
  boat: String,
  rename: String,
  renameBoat: String,
  addBoat: String,
  newName: String,
  token: String,
  tokenText: String,
  tokenTextLong: String
)
object BoatLang:
  implicit val json: Codec[BoatLang] = deriveCodec[BoatLang]

case class InviteLang(
  invites: String,
  noInvites: String,
  invite: String,
  placeholder: String,
  friends: String,
  noFriends: String,
  state: String,
  email: String,
  revoke: String,
  accept: String,
  accepted: String,
  reject: String,
  rejected: String,
  awaiting: String,
  from: String
) derives Codec.AsObject:
  def confirmRevoke(boat: BoatName, fromEmail: Email) =
    s"$revoke $boat $from $fromEmail?"

case class SettingsLang(
  welcome: String,
  welcomeText: String,
  laterText: String,
  notifications: String,
  notificationsText: String,
  howItWorks: String,
  signIn: String,
  signInText: String,
  signInWith: String,
  giveTrackTitle: String,
  edit: String,
  cancel: String,
  back: String,
  done: String,
  noTracksHelp: String,
  formats: FormatsLang,
  boatLang: BoatLang,
  actions: String,
  delete: String,
  invite: InviteLang
)

object SettingsLang:
  val modern = deriveCodec[SettingsLang]
  val encoder: Encoder[SettingsLang] = (sl: SettingsLang) =>
    modern(sl).deepMerge(BoatLang.json(sl.boatLang))
  implicit val codec: Codec[SettingsLang] = Codec.from(modern, encoder)

case class LanguageInfo(name: String, code: String) derives Codec.AsObject

case class LimitTypes(
  speedLimit: String,
  noWaves: String,
  noWindSurfing: String,
  noJetSkiing: String,
  noMotorPower: String,
  noAnchoring: String,
  noStopping: String,
  noAttachment: String,
  noOvertaking: String,
  noRendezVous: String,
  speedRecommendation: String,
  unknown: String
) derives Codec.AsObject

case class LimitLang(
  limit: String,
  magnitude: String,
  length: String,
  location: String,
  fairwayName: String,
  responsible: String,
  types: LimitTypes,
  signs: TrafficSignLang
) derives Codec.AsObject

object LimitLang:
  private val limitsFi = LimitTypes(
    "Nopeusrajoitus",
    "Aallokon aiheuttamisen kielto",
    "Purjelautailukielto",
    "Vesiskootterilla ajo kielletty",
    "Aluksen kulku moottorivoimaa käyttäen kielletty",
    "Ankkurin käyttökielto",
    "Pysäköimiskielto",
    "Kiinnittymiskielto",
    "Ohittamiskielto",
    "Kohtaamiskielto",
    "Nopeussuositus",
    "Tuntematon"
  )
  val fi = LimitLang(
    "Rajoitukset",
    "Nopeus",
    "Pituus",
    "Sijainti",
    "Väylän nimi",
    "Merkinnästä vastaava",
    limitsFi,
    TrafficSignLang(
      TrafficSignLimitsLang(
        "Tuntematon",
        "Vesihiihtokielto",
        "Pysähtymismerkki",
        "Yleinen varoitusmerkki",
        "Annettava äänimerkki",
        "Rajoitettu alikulkukorkeus",
        "Rajoitettu kulkusyvyys",
        "Rajoitettu kulkuleveys",
        limitsFi
      ),
      TrafficSignInfoLang(
        "Voimakas virtaus",
        "Väylän reuna",
        "Varoitus uimapaikasta",
        "Otettava yhteys radiopuhelimella",
        "Pysäköiminen sallittu",
        "Kiinnittyminen sallittu",
        "Ilmajohto",
        "Puhelin",
        "Lauttaväylän risteäminen, lossi",
        "Lauttaväylän risteäminen, lautta",
        "Mahdollisuus radiopuhelinyhteyteen",
        "Juomavesipiste",
        "Kiellon, määräyksen tai rajoituksen päättyminen",
        "Kaapelitaulu",
        "Johtotaulu",
        "Suuntamerkki, ylempi",
        "Suuntamerkki, alempi"
      ),
      TrafficSignTypeLang(
        "Tuntematon",
        "Kieltomerkki",
        "Määräys- tai rajoitusmerkki",
        "Tiedotusmerkki",
        "Kaapeli- tai johtotaulu",
        "Suuntamerkki",
        "Valo-opaste"
      )
    )
  )
  private val limitsSe = LimitTypes(
    "Hastighetsbegränsning",
    "Förbjudet att orsaka vågor",
    "Vindsurfing förbjudet",
    "Förbjudet att åka vattenskoter",
    "Motorkraft förbjudet",
    "Förbjudet att använda ankare",
    "Förbjudet att stanna",
    "Förbjudet att stanna",
    "Förbjudet att passera",
    "Mötesförbud",
    "Hastighetsrekommendation",
    "Okänd"
  )
  val se = LimitLang(
    "Begränsningar",
    "Hastighet",
    "Längd",
    "Plats",
    "Farledens namn",
    "Ansvarig",
    limitsSe,
    TrafficSignLang(
      TrafficSignLimitsLang(
        "Okänd",
        "Förbjudet att vattenskida",
        "Stop-märke",
        "Allmänt varningsmärke",
        "Måste ge ljudsignal",
        "Begränsad höjd",
        "Begränsat djup",
        "Begränsad bredd",
        limitsSe
      ),
      TrafficSignInfoLang(
        "Stark ström",
        "Farledens gräns",
        "Varning för simplats",
        "Bör ta radiokontakt",
        "Tillåtet att parkera",
        "Tillåtet att stanna",
        "Luftkabel",
        "Telefon",
        "Korsande kabelfärja",
        "Korsande färja",
        "Möjligt att ta radiokontakt",
        "Drickspunkt, vatten",
        "Begränsning eller förbud tar slut",
        "Kabeltavla",
        "Ledningstavla",
        "Riktningsmärke, övre",
        "Riktningsmärke, nedre"
      ),
      TrafficSignTypeLang(
        "Okänd",
        "Förbudsmärke",
        "Begränsningsmärke",
        "Informationsmärke",
        "Kabel- eller ledningstavla",
        "Riktningsmärke",
        "Ljus"
      )
    )
  )
  private val limitsEn = LimitTypes(
    "Speed limit",
    "No waves",
    "No windsurfing",
    "No jet skiing",
    "No motor power",
    "No anchoring",
    "No stopping",
    "No stopping",
    "No passing",
    "No meeting",
    "Speed recommendation",
    "Unknown"
  )
  val en = LimitLang(
    "Limits",
    "Speed",
    "Length",
    "Location",
    "Fairway name",
    "Responsible",
    limitsEn,
    TrafficSignLang(
      TrafficSignLimitsLang(
        "Unknown",
        "Waterskiing forbidden",
        "Stop sign",
        "General warning",
        "Must make sound",
        "Limited height",
        "Limited depth",
        "Limited width",
        limitsEn
      ),
      TrafficSignInfoLang(
        "Strong current",
        "Fairway limit",
        "Swimmers warning",
        "Must make radio contact",
        "Parking allowed",
        "Stopping allowed",
        "Air cable",
        "Telephone",
        "Cable ferry crossing",
        "Ferry crossing",
        "Radio contact possibility",
        "Drinking point, water",
        "End of warning or limit",
        "Cable sign",
        "Wire sign",
        "Direction sign, upper",
        "Direction sign, lower"
      ),
      TrafficSignTypeLang(
        "Unknown",
        "Not allowed",
        "Limit",
        "Informational",
        "Cable or wire",
        "Direction",
        "Light assistance"
      )
    )
  )

case class LabelsLang(statistics: String, monthly: String, yearly: String, allTime: String)
  derives Codec.AsObject

case class MonthsLang(
  jan: String,
  feb: String,
  mar: String,
  apr: String,
  may: String,
  jun: String,
  jul: String,
  aug: String,
  sep: String,
  oct: String,
  nov: String,
  dec: String
) derives Codec.AsObject:
  def apply(month: MonthVal) = month.value match
    case 1  => jan
    case 2  => feb
    case 3  => mar
    case 4  => apr
    case 5  => may
    case 6  => jun
    case 7  => jul
    case 8  => aug
    case 9  => sep
    case 10 => oct
    case 11 => nov
    case 12 => dec
    case _  => ""

object MonthsLang:
  val se = apply(
    "Januari",
    "Februari",
    "Mars",
    "April",
    "Maj",
    "Juni",
    "Juli",
    "Augusti",
    "September",
    "Oktober",
    "November",
    "December"
  )
  val fi = apply(
    "Tammikuu",
    "Helmikuu",
    "Maaliskuu",
    "Huhtikuu",
    "Toukokuu",
    "Kesäkuu",
    "Heinäkuu",
    "Elokuu",
    "Syyskuu",
    "Lokakuu",
    "Marraskuu",
    "Joulukuu"
  )
  val en = apply(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
  )

case class CalendarLang(months: MonthsLang) derives Codec.AsObject

case class AppMetaLang(appName: String, version: String, build: String) derives Codec.AsObject

case class Lang(
  appName: String,
  map: String,
  language: Language,
  name: String,
  qualityClass: String,
  time: String,
  comparisonLevel: String,
  appMeta: AppMetaLang,
  specialWords: SpecialWords,
  fairway: FairwayLang,
  track: TrackLang,
  mark: MarkLang,
  ais: AisLang,
  shipTypes: ShipTypesLang,
  attributions: AttributionInfo,
  profile: ProfileLang,
  messages: MessagesLang,
  settings: SettingsLang,
  limits: LimitLang,
  labels: LabelsLang,
  calendar: CalendarLang
) derives Codec.AsObject

object Lang:
  private val appName = "Boat-Tracker"

  def apply(language: Language): Lang = language match
    case Language.swedish => se
    case Language.finnish => fi
    case Language.english => en
    case _                => default

  val en = Lang(
    appName,
    "Map",
    language = Language.english,
    name = "Name",
    qualityClass = "Quality",
    time = "Time",
    comparisonLevel = "Comparison",
    AppMetaLang(appName, "Version", "build"),
    specialWords = SpecialWords(
      transportAgency = "Finnish Transport Agency",
      transportInfrastructureAgency = "Finnish Transport Infrastructure Agency",
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
      "Trail",
      "Boats",
      "Trails",
      "Days",
      "Routes",
      "Speed",
      "Water",
      "Depth",
      "Top",
      "Time",
      "Hours",
      "Distance",
      "Top Speed",
      "Avg Speed",
      "Temperature",
      "Water Temp",
      "Date",
      "Track History",
      "Graph",
      "Coordinates",
      "Comments",
      EnvLang("Altitude")
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
      aidTypes = AidTypeLang.en,
      flotation = FlotationLang.en,
      lit = "Lit",
      yes = "Yes",
      no = "No",
      extraInfo1 = "Extra info 1",
      extraInfo2 = "Extra info 2"
    ),
    ais = AisLang(
      draft = "Draft",
      destination = "Destination",
      shipType = "Ship type"
    ),
    shipTypes = ShipTypesLang.en,
    attributions = AttributionInfo.en,
    ProfileLang(
      "Username",
      "Signed in as",
      "Logout",
      "Choose Identity Provider",
      "Language",
      "Choose Language",
      "Suomeksi",
      "Svenska",
      "English",
      "Delete account",
      "Are you sure you want to delete your account?"
    ),
    MessagesLang("Loading...", "Failed to load profile,", "No saved tracks.", "N/A"),
    SettingsLang(
      "Welcome",
      "Add this token to the Boat-Tracker agent software in your boat to save tracks to this app:",
      "You can later view this token in the Boats section of the app.",
      "Notifications",
      s"Turn on to receive notifications when your boat connects or disconnects from $appName.",
      "How it works",
      "Sign In",
      "Sign in to view past tracks driven with your boat.",
      "Sign in with",
      "Name the track",
      "Edit",
      "Cancel",
      "Back",
      "Done",
      s"Hello! You have no saved tracks. To save tracks, you'll need to connect the $appName agent software to the GPS chartplotter in your boat.",
      FormatsLang("dd MMM yyyy", "HH:mm:ss", "HH:mm", "dd MMM yyyy HH:mm:ss"),
      BoatLang(
        "Boat",
        "Rename",
        "Rename Boat",
        "Add boat",
        "Provide a new name",
        "Token",
        s"Add the token to the $appName agent software running in your boat. For more information, see https://www.boat-tracker.com/docs/agent.",
        s"Add the token provided after sign in to the $appName agent software running in your boat. Subsequently, tracks driven with the boat are saved to your account and can be viewed in this app. For agent installation instructions, see https://www.boat-tracker.com/docs/agent."
      ),
      "Actions",
      "Delete",
      InviteLang(
        "Invites",
        "No invites.",
        "Invite",
        "Email",
        "Friends",
        "No friends.",
        "State",
        "Email",
        "Revoke",
        "Accept",
        "Accepted",
        "Reject",
        "Rejected",
        "Awaiting",
        "from"
      )
    ),
    LimitLang.en,
    LabelsLang("Statistics", "Monthly", "Yearly", "All time"),
    CalendarLang(MonthsLang.en)
  )

  val fi = Lang(
    appName,
    "Kartta",
    language = Language.finnish,
    name = "Nimi",
    qualityClass = "Laatuluokka",
    time = "Aika",
    comparisonLevel = "Vertaustaso",
    AppMetaLang(appName, "Versio", "paketti"),
    specialWords = SpecialWords(
      transportAgency = "Liikennevirasto",
      transportInfrastructureAgency = "Väylävirasto",
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
      "Ura",
      "Veneet",
      "Urat",
      "Ajopäivät",
      "Ajokerrat",
      "Nopeus",
      "Vesi",
      "Syvyys",
      "Huippu",
      "Kesto",
      "Ajotunnit",
      "Matka",
      "Huippunopeus",
      "Keskinopeus",
      "Lämpötila",
      "Veden lämpötila",
      "Päivämäärä",
      "Edelliset",
      "Käyrät",
      "Koordinaatit",
      "Kommentit",
      EnvLang("Korkeus")
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
      aidTypes = AidTypeLang.fi,
      flotation = FlotationLang.fi,
      lit = "Valaistu",
      yes = "Kyllä",
      no = "Ei",
      extraInfo1 = "Lisätieto 1",
      extraInfo2 = "Lisätieto 2"
    ),
    ais = AisLang(
      draft = "Syväys",
      destination = "Määränpää",
      shipType = "Alus"
    ),
    shipTypes = ShipTypesLang.fi,
    AttributionInfo.fi,
    ProfileLang(
      "Käyttäjätunnus",
      "Käyttäjätunnus",
      "Kirjaudu ulos",
      "Valitse kirjautuminen",
      "Kieli",
      "Valitse kieli",
      "Suomeksi",
      "Svenska",
      "English",
      "Poista tili",
      "Haluatko varmasti poistaa tilisi?"
    ),
    MessagesLang(
      "Laddar...",
      "Käyttäjätietojen lataus epäonnistui.",
      "Ei tallennettuja reittejä.",
      "N/A"
    ),
    SettingsLang(
      "Welcome",
      s"Lisää tämä avain veneeseen asennettuun $appName -sovellukseen tallentaaksesi ajettuja reittejä:",
      "Näet tämän avaimen myöhemmin myös sovelluksen Veneet -osiossa.",
      "Notifikaatio",
      s"Vastaanota notifikaatio kun veneesi yhdistää $appName -sovellukseen.",
      "Kuinka tämä toimii",
      "Kirjaudu sisään",
      "Kirjaudu sisään ja tallenna ajetut matkat",
      "Kirjaudu palvelulla",
      "Nimeä",
      "Muokkaa",
      "Keskeytä",
      "Takaisin",
      "Valmis",
      s"Hei! Ei tallennettuja reittejä. Reittien tallennus vaatii $appName -sovelluksen yhdistämisen veneesi karttaplotteriin.",
      FormatsLang("dd.MM.yyyy", "HH:mm:ss", "HH:mm", "dd.MM.yyyy HH:mm:ss"),
      BoatLang(
        "Vene",
        "Uusi nimi",
        "Nimeä vene",
        "Lisää vene",
        "Uusi nimi",
        "Avain",
        s"Lisää avain veneeseen asennettuun $appName -sovellukseen. Lisätietoja saat osoitteesta https://docs.boat-tracker.com/agent/.",
        "Kirjautumisen jälkeen saat avaimen, jolla tallennat ajetut matkat käyttäjätunnuksellesi. Lisätietoja saat osoitteesta https://docs.boat-tracker.com/agent/."
      ),
      "Toimenpiteet",
      "Poista",
      InviteLang(
        "Kutsutut",
        "Ei kutsuttuja.",
        "Kutsu",
        "Sähköposti",
        "Ystävät",
        "Ei ystäviä.",
        "Tila",
        "Sähköposti",
        "Poista",
        "Hyväksy",
        "Hyväksytty",
        "Kiellä",
        "Kielletty",
        "Odottaa",
        "käyttäjältä"
      )
    ),
    LimitLang.fi,
    LabelsLang("Tilastot", "Kuukausittain", "Vuosittain", "Kaikki"),
    CalendarLang(MonthsLang.fi)
  )

  val se = Lang(
    appName,
    "Karta",
    language = Language.swedish,
    name = "Namn",
    qualityClass = "Kvalitet",
    time = "Tid",
    comparisonLevel = "Jämförelse",
    AppMetaLang(appName, "Version", "paket"),
    specialWords = SpecialWords(
      transportAgency = "Trafikverket",
      transportInfrastructureAgency = "Trafikledsverket",
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
      "Spår",
      "Båtar",
      "Spår",
      "Dagar",
      "Rutter",
      "Hastighet",
      "Vatten",
      "Djup",
      "Max",
      "Tid",
      "Timmar",
      "Avstånd",
      "Max hastighet",
      "Medelhastighet",
      "Temperatur",
      "Vattentemperatur",
      "Datum",
      "Tidigare",
      "Visualisering",
      "Koordinater",
      "Kommentarer",
      EnvLang("Höjd")
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
      aidTypes = AidTypeLang.se,
      flotation = FlotationLang.se,
      lit = "Belyst",
      yes = "Ja",
      no = "Nej",
      extraInfo1 = "Extra info 1",
      extraInfo2 = "Extra info 2"
    ),
    ais = AisLang(
      draft = "Djupgående",
      destination = "Destination",
      shipType = "Fartyg"
    ),
    shipTypes = ShipTypesLang.se,
    AttributionInfo.se,
    ProfileLang(
      "Användarnamn",
      "Inloggad som",
      "Logga ut",
      "Identifiera dig",
      "Språk",
      "Välj språk",
      "Suomeksi",
      "Svenska",
      "English",
      "Radera konto",
      "Är du säker att du vill radera ditt konto?"
    ),
    MessagesLang("Laddar...", "Laddning av profildata misslyckades.", "Inga sparade spår.", "N/A"),
    SettingsLang(
      "Välkommen",
      s"Spara den här nyckeln i $appName-appen installerad i din båt för att spara körda rutter:",
      "Du kan senare läsa nyckeln från sidan Båtar i den här appen.",
      "Notifikationer",
      s"Få notifikationer när din båt är uppkopplad till $appName.",
      "Hur det fungerar",
      "Logga in",
      "Logga in för att spara spår",
      "Logga in med",
      "Namnge spår",
      "Redigera",
      "Avbryt",
      "Tillbaka",
      "Färdig",
      s"Inga sparade spår. För att spara spår, koppla $appName-appen till båtens plotter.",
      FormatsLang("dd.MM.yyyy", "HH:mm:ss", "HH:mm", "dd.MM.yyyy HH:mm:ss"),
      BoatLang(
        "Båt",
        "Ändra namn",
        "Namnge båt",
        "Lägg till båt",
        "Ange ett nytt namn",
        "Nyckel",
        s"Spara nyckeln i $appName-appen installerad i din båt. För mera information, se https://docs.boat-tracker.com/agent/.",
        s"Inloggning skapar en nyckel du kan spara i $appName-appen installerad i din båt. Med nyckeln sparas körda spår under ditt användarnamn. För mera information, se https://docs.boat-tracker.com/agent/."
      ),
      "Ändringar",
      "Radera",
      InviteLang(
        "Inbjudningar",
        "Inga inbjudningar.",
        "Bjud in",
        "E-post",
        "Vänner",
        "Inga vänner.",
        "Status",
        "E-post",
        "Ta bort",
        "Acceptera",
        "Accepterad",
        "Tacka nej",
        "Tackade nej",
        "Väntar",
        "från"
      )
    ),
    LimitLang.se,
    LabelsLang("Statistik", "Per månad", "Per år", "Alla tider"),
    CalendarLang(MonthsLang.se)
  )
  val default = fi
