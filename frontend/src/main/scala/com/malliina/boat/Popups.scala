package com.malliina.boat

import com.malliina.measure.{DistanceM, SpeedM}
import scalatags.JsDom.all.*

class Popups(lang: Lang) extends BoatModels:
  val empty = modifier()
  val trackLang = lang.track
  private val markLang = lang.mark
  private val fairwayLang = lang.fairway
  private val aisLang = lang.ais
  val specialWords = lang.specialWords
  private val limitsLang = lang.limits

  def track(point: PointProps) =
    val isBoat = point.sourceType == SourceType.Boat
    titledTable(point.boatName.name)(
      row(
        trackLang.speed,
        if isBoat then formatSpeed(point.speed)
        else formatSpeedKph(point.speed)
      ),
      if isBoat then
        modifier(
          row(trackLang.water, point.waterTemp.formatCelsius),
          row(trackLang.depth, point.depth.short)
        )
      else empty,
      point.outsideTemp.fold(empty)(ot => row(trackLang.temperature, ot.formatCelsius)),
      point.altitude.fold(empty)(a => row(trackLang.env.altitude, formatDistance(a))),
      tr(td(colspan := 2)(point.dateTime))
    )

  def device(point: DeviceProps) =
    titledTable(point.deviceName.name)(
      tr(td(colspan := 2)(point.dateTime))
    )

  def ais(vessel: VesselInfo) =
//    val unknownShip = vessel.shipType.isInstanceOf[ShipType.Unknown]
    titledTable(vessel.name)(
      vessel.destination.fold(empty)(d => row(aisLang.destination, d)),
//      if !unknownShip then row(aisLang.shipType, vessel.shipType.name(lang.shipTypes)) else empty,
      row(trackLang.speed, formatSpeed(vessel.sog)),
      row(aisLang.draft, formatDistance(vessel.draft)),
      row(lang.time, vessel.timestampFormatted)
      // row(lang.duration, vessel.eta)
    )

  def formatSpeed(s: SpeedM) = "%.2f kn".format(s.toKnots)
  def formatSpeedKph(s: SpeedM) = "%.2f km/h".format(s.toKmh)

  def formatDistance(d: DistanceM) = "%.1f m".format(d.toMeters)

  private def asMeters(d: DistanceM) =
    val value = "%.2f"
      .format(d.toMeters)
      .stripSuffix("0")
      .stripSuffix("0")
      .stripSuffix(".")
    s"$value m"

  def mark(symbol: MarineSymbol) =
    titledTable(symbol.name(lang).fold("")(identity))(
      row(markLang.aidType, symbol.aidType.translate(markLang.aidTypes)),
      symbol.construction
        .fold(empty)(c => row(markLang.construction, c.translate(markLang.structures))),
      if symbol.navMark == NavMark.NotApplicable then empty
      else row(markLang.navigation, symbol.navMark.translate(markLang.navTypes)),
      symbol.location(lang).fold(empty)(l => row(markLang.location, l)),
      row(markLang.owner, symbol.ownerName(specialWords)),
      row(markLang.lit, if symbol.lit then markLang.yes else markLang.no)
    )

  def minimalMark(symbol: MinimalMarineSymbol) =
    titledTable(symbol.name(lang).fold("")(identity))(
      symbol.trafficMarkType.fold(empty)(tmt =>
        row(markLang.markType, tmt.translate(limitsLang.types))
      ),
      symbol.speed.fold(empty)(speed => row(limitsLang.magnitude, s"${speed.toKmh.toInt} km/h")),
      symbol.location(lang).fold(empty)(l => row(markLang.location, l)),
      symbol.influence.fold(empty)(i => row(markLang.influence, i.translate(fairwayLang.zones))),
      row(markLang.owner, symbol.ownerName(specialWords)),
      symbol.extraInfo1.fold(empty)(e1 => row(markLang.extraInfo1, e1)),
      symbol.extraInfo2.fold(empty)(e2 => row(markLang.extraInfo2, e2))
    )

  def fairway(fairway: FairwayArea, more: Modifier*) =
    titledTable(fairway.ownerName(specialWords))(
      row(fairwayLang.fairwayType, fairway.fairwayType.translate(fairwayLang.types)),
      row(fairwayLang.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(fairwayLang.harrowDepth, asMeters(fairway.harrowDepth)),
      fairway.markType
        .fold(empty)(markType => row(markLang.markType, markType.translate(markLang.types))),
      more
    )

  def fairwayInfo(info: FairwayInfo) =
    titledTable(info.name(lang).fold("")(identity))(
      info.bestDepth.fold(empty) { depth =>
        row(fairwayLang.fairwayDepth, formatDistance(depth))
      }
    )

  def depthArea(depthArea: DepthArea) =
    popupTable(
      row(fairwayLang.minDepth, asMeters(depthArea.minDepth)),
      row(fairwayLang.maxDepth, asMeters(depthArea.maxDepth))
    )

  def limitArea(limit: LimitArea) = popupTable(
    limit.responsible.fold(empty)(r => titleRow(r)),
    limitContent(limit)
  )

  private def limitContent(limit: LimitArea) = modifier(
    row(limitsLang.limit, describeItems(limit.describeTypes(limitsLang.types))),
    limit.limit.fold(empty)(speed => row(limitsLang.magnitude, s"${speed.toKmh.toInt} km/h")),
    limit.location.fold(empty)(l => row(limitsLang.location, l)),
    limit.fairwayName.fold(empty)(f => row(limitsLang.fairwayName, f))
  )

  private def describeItems(items: Seq[String]): Modifier =
    if items.length > 1 then ul(`class` := "popup-list")(items.map(i => li(i)))
    else items.mkString(", ")

  def limitedFairway(limit: LimitArea, area: FairwayArea) =
    fairway(
      area,
      limitContent(limit)
    )

  private def titledTable(title: Modifier)(content: Modifier*) =
    popupTable(
      titleRow(title),
      content
    )

  private def titleRow(title: Modifier) = tr(td(colspan := 2, `class` := "popup-title")(title))

  private def popupTable(content: Modifier*) =
    table(`class` := "boat-popup")(
      tbody(
        content
      )
    )

  private def row(title: String, value: Modifier) =
    tr(td(`class` := "popup-label")(title), td(value))

  def trophyMarker(speed: SpeedM) =
    i(`class` := "fas fa-trophy marker-top-speed")
