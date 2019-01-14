package com.malliina.boat

import com.malliina.measure.{Distance, DistanceM, Speed}
import scalatags.JsDom.all._

object Popups {
  def apply(lang: Lang) = new Popups(lang)
}

class Popups(lang: Lang) extends BoatModels {
  val empty = modifier()

  def track(c: TimedCoord, from: TrackRef) =
    titledTable(from.boatName.name)(
      row(lang.speed, formatSpeed(c.speed)),
      row(lang.water, c.waterTemp.formatCelsius),
      row(lang.depth, c.depth.short),
      tr(td(colspan := 2)(c.boatTime.dateTime))
    )

  def ais(vessel: VesselInfo) = {
    val unknownShip = vessel.shipType.isInstanceOf[ShipType.Unknown]
    titledTable(vessel.name)(
      vessel.destination.fold(empty)(d => row(lang.destination, d)),
      if (!unknownShip) row(lang.shipType, vessel.shipType.name(lang.shipTypes)) else empty,
      row(lang.speed, formatSpeed(vessel.sog)),
      row(lang.draft, formatDistance(vessel.draft)),
      row(lang.time, vessel.timestampFormatted)
      // row(lang.duration, vessel.eta)
    )
  }

  def formatSpeed(s: Speed) = "%.2f kn".format(s.toKnots)

  def formatDistance(d: DistanceM) = "%.1f m".format(d.toMeters)

  def mark(symbol: MarineSymbol) =
    titledTable(symbol.ownerName(lang))(
      row(lang.`type`, symbol.aidType.in(lang)),
      symbol.construction.fold(empty)(c => row(lang.construction, c.in(lang))),
      if (symbol.navMark == NavMark.NotApplicable) empty
      else row(lang.navigation, symbol.navMark.in(lang)),
      symbol.name(lang).fold(empty)(n => row(lang.name, n)),
      symbol.location(lang).fold(empty)(l => row(lang.location, l))
    )

  def minimalMark(symbol: MinimalMarineSymbol) =
    titledTable(symbol.ownerName(lang))(
      symbol.name(lang).fold(empty)(n => row(lang.name, n)),
      symbol.location(lang).fold(empty)(l => row(lang.location, l)),
      row(lang.influence, symbol.influence.in(lang))
    )

  def fairway(fairway: FairwayArea) =
    titledTable(fairway.ownerName(lang))(
      row(lang.fairwayType, fairway.fairwayType.in(lang)),
      row(lang.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(lang.harrowDepth, asMeters(fairway.harrowDepth)),
      fairway.markType.fold(empty)(markType => row(lang.markType, markType.in(lang)))
    )

  def depthArea(depthArea: DepthArea) =
    popupTable(
      row(lang.minDepth, asMeters(depthArea.minDepth)),
      row(lang.maxDepth, asMeters(depthArea.maxDepth))
    )

  private def asMeters(d: Distance) = {
    val value = "%.2f".format(d.toMetersDouble)
      .stripSuffix("0")
      .stripSuffix("0")
      .stripSuffix(".")
    s"$value m"
  }

  private def titledTable(title: Modifier)(content: Modifier*) =
    popupTable(
      tr(td(colspan := 2)(title)),
      content
    )

  private def popupTable(content: Modifier*) =
    table(`class` := "boat-popup")(
      tbody(
        content
      )
    )

  private def row(title: String, value: Modifier) =
    tr(td(`class` := "popup-title")(title), td(value))

  def marker(speed: Speed) =
    i(`class` := "fas fa-trophy marker-top-speed")
}
