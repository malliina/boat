package com.malliina.boat

import cats.syntax.show.toShow
import com.malliina.boat.parking.ParkingProps
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

  private val parkingLang = lang.cars.parking

  def parking(props: ParkingProps) =
    titledTable(parkingLang.parking)(
      props.label.fold(empty): text =>
        row(parkingLang.description, text, valueClass = Option("tooltip-value")),
      rowOpt(parkingLang.validity, props.validity),
      rowOpt(parkingLang.duration, props.duration),
      rowOpt(parkingLang.residentialParkingSign, props.residentialParkingSign)
    )

  def track(point: PointProps) =
    val isBoat = point.sourceType == SourceType.Boat
    titledTable(point.boatName.show)(
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
      rowOpt(trackLang.battery, point.battery.map(_.formatKwh)),
      rowOpt(trackLang.temperature, point.outsideTemp.map(_.formatCelsius)),
      rowOpt(trackLang.env.altitude, point.altitude.map(formatDistance)),
      tr(td(colspan := 2)(point.dateTime))
    )

  def device(point: DeviceProps) =
    titledTable(point.deviceName.show)(
      tr(td(colspan := 2)(point.dateTime))
    )

  def ais(vessel: VesselInfo) =
//    val unknownShip = vessel.shipType.isInstanceOf[ShipType.Unknown]
    titledTable(vessel.name)(
      rowOpt(aisLang.destination, vessel.destination),
//      if !unknownShip then row(aisLang.shipType, vessel.shipType.name(lang.shipTypes)) else empty,
      row(trackLang.speed, formatSpeed(vessel.sog)),
      row(aisLang.draft, formatDistance(vessel.draft)),
      row(lang.time, vessel.timestampFormatted)
      // row(lang.duration, vessel.eta)
    )

  def aisSimple(vessel: VesselTrail) =
    //    val unknownShip = vessel.shipType.isInstanceOf[ShipType.Unknown]
    titledTable(vessel.name)(
//      rowOpt(aisLang.destination, vessel.destination),
      //      if !unknownShip then row(aisLang.shipType, vessel.shipType.name(lang.shipTypes)) else empty,
//      row(trackLang.speed, formatSpeed(vessel.sog)),
      row(aisLang.draft, formatDistance(vessel.draft)),
      vessel.updates.headOption.fold(empty): latest =>
        row(lang.time, latest.added.dateTime)
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
      rowOpt(markLang.construction, symbol.construction.map(_.translate(markLang.structures))),
      if symbol.navMark == NavMark.NotApplicable then empty
      else row(markLang.navigation, symbol.navMark.translate(markLang.navTypes)),
      rowOpt(markLang.location, symbol.location(lang)),
      row(markLang.owner, symbol.ownerName(specialWords)),
      row(markLang.lit, if symbol.lit then markLang.yes else markLang.no)
    )

  def minimalMark(symbol: MinimalMarineSymbol) =
    titledTable(symbol.name(lang).fold("")(identity))(
      rowOpt(markLang.markType, symbol.trafficMarkType.map(_.translate(limitsLang.types))),
      rowOpt(limitsLang.magnitude, symbol.speed.map(speed => s"${speed.toKmh.toInt} km/h")),
      rowOpt(markLang.location, symbol.location(lang)),
      symbol.influence.fold(empty)(i => row(markLang.influence, i.translate(fairwayLang.zones))),
      row(markLang.owner, symbol.ownerName(specialWords)),
      rowOpt(markLang.extraInfo1, symbol.extraInfo1),
      rowOpt(markLang.extraInfo2, symbol.extraInfo2)
    )

  def fairway(fairway: FairwayArea, more: Modifier*) =
    titledTable(fairway.ownerName(specialWords))(
      row(fairwayLang.fairwayType, fairway.fairwayType.translate(fairwayLang.types)),
      row(fairwayLang.fairwayDepth, asMeters(fairway.fairwayDepth)),
      row(fairwayLang.harrowDepth, asMeters(fairway.harrowDepth)),
      rowOpt(markLang.markType, fairway.markType.map(mt => mt.translate(markLang.types))),
      more
    )

  def fairwayInfo(info: FairwayInfo) =
    titledTable(info.name(lang).fold("")(identity))(
      info.bestDepth.fold(empty): depth =>
        row(fairwayLang.fairwayDepth, formatDistance(depth))
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
    rowOpt(limitsLang.magnitude, limit.limit.map(speed => s"${speed.toKmh.toInt} km/h")),
    rowOpt(limitsLang.location, limit.location),
    rowOpt(limitsLang.fairwayName, limit.fairwayName)
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

  private def rowOpt(title: String, value: Option[String]) =
    value.fold(empty)(v => row(title, v))

  private def row(title: String, value: Modifier, valueClass: Option[String] = None) =
    tr(td(`class` := "popup-label")(title), valueClass.fold(td(value))(vc => td(cls := vc)(value)))
