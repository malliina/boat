package com.malliina.boat

import com.malliina.mapbox.LngLatLike
import com.malliina.measure.{Distance, DistanceM, Speed}
import scalatags.JsDom.all._

object Popups {
  def apply(lang: Lang) = new Popups(lang)
}

class Popups(lang: Lang) extends BoatModels {
  val empty = modifier()
  val trackLang = lang.track
  val markLang = lang.mark
  val fairwayLang = lang.fairway
  val aisLang = lang.ais
  val specialWords = lang.specialWords

  def track(c: TimedCoord, from: TrackRef) =
    titledTable(from.boatName.name)(
      row(trackLang.speed, formatSpeed(c.speed)),
      row(trackLang.water, c.waterTemp.formatCelsius),
      row(trackLang.depth, c.depth.short),
      tr(td(colspan := 2)(c.time.dateTime))
    )

  def ais(vessel: VesselInfo) = {
    val unknownShip = vessel.shipType.isInstanceOf[ShipType.Unknown]
    titledTable(vessel.name)(
      vessel.destination.fold(empty)(d => row(aisLang.destination, d)),
      if (!unknownShip) row(aisLang.shipType, vessel.shipType.name(lang.shipTypes)) else empty,
      row(trackLang.speed, formatSpeed(vessel.sog)),
      row(aisLang.draft, formatDistance(vessel.draft)),
      row(lang.time, vessel.timestampFormatted)
      // row(lang.duration, vessel.eta)
    )
  }

  def formatSpeed(s: Speed) = "%.2f kn".format(s.toKnots)

  def formatDistance(d: DistanceM) = "%.1f m".format(d.toMeters)

  private def asMeters(d: Distance) = {
    val value = "%.2f"
      .format(d.toMetersDouble)
      .stripSuffix("0")
      .stripSuffix("0")
      .stripSuffix(".")
    s"$value m"
  }

  def mark(symbol: MarineSymbol) =
    titledTable(symbol.name(lang).fold("")(identity))(
      row(markLang.aidType, symbol.aidType.translate(markLang.aidTypes)),
      symbol.construction.fold(empty)(c =>
        row(markLang.construction, c.translate(markLang.structures))),
      if (symbol.navMark == NavMark.NotApplicable) empty
      else row(markLang.navigation, symbol.navMark.translate(markLang.navTypes)),
      symbol.location(lang).fold(empty)(l => row(markLang.location, l)),
      row(markLang.owner, symbol.ownerName(specialWords))
    )

  def minimalMark(symbol: MinimalMarineSymbol) =
    titledTable(symbol.name(lang).fold("")(identity))(
      symbol.location(lang).fold(empty)(l => row(markLang.location, l)),
      row(markLang.influence, symbol.influence.translate(fairwayLang.zones)),
      row(markLang.owner, symbol.ownerName(specialWords))
    )

  def fairway(fairway: FairwayArea) =
    titledTable(fairway.ownerName(specialWords))(
      row(fairwayLang.fairwayType, fairway.fairwayType.translate(fairwayLang.types)),
      row(fairwayLang.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(fairwayLang.harrowDepth, asMeters(fairway.harrowDepth)),
      fairway.markType.fold(empty)(markType =>
        row(markLang.markType, markType.translate(markLang.types)))
    )

  def fairwayInfo(info: FairwayInfo) =
    titledTable(info.name(lang).fold("")(identity))(
      row(fairwayLang.fairwayDepth, formatDistance(info.depth)),
    )

  def depthArea(depthArea: DepthArea) =
    popupTable(
      row(fairwayLang.minDepth, asMeters(depthArea.minDepth)),
      row(fairwayLang.maxDepth, asMeters(depthArea.maxDepth))
    )

  private def titledTable(title: Modifier)(content: Modifier*) =
    popupTable(
      tr(td(colspan := 2, `class` := "popup-title")(title)),
      content
    )

  private def popupTable(content: Modifier*) =
    table(`class` := "boat-popup")(
      tbody(
        content
      )
    )

  private def row(title: String, value: Modifier) =
    tr(td(`class` := "popup-label")(title), td(value))

  def marker(speed: Speed) =
    i(`class` := "fas fa-trophy marker-top-speed")
}
