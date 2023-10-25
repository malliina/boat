package com.malliina.boat

import com.malliina.boat.Parsing.validate
import com.malliina.mapbox.*
import com.malliina.util.EitherOps

sealed trait ClickType:
  def target: LngLatLike

case class VesselClick(props: VesselProps, target: LngLatLike) extends ClickType
case class TrophyClick(trophy: PointProps, target: LngLatLike) extends ClickType
case class DeviceClick(device: DeviceProps, target: LngLatLike) extends ClickType
case class SymbolClick(symbol: MarineSymbol, target: LngLatLike) extends ClickType
case class MinimalClick(symbol: MinimalMarineSymbol, target: LngLatLike) extends ClickType
case class FairwayClick(area: FairwayArea, target: LngLatLike) extends ClickType
case class FairwayInfoClick(info: FairwayInfo, target: LngLatLike) extends ClickType
case class DepthClick(area: DepthArea, target: LngLatLike) extends ClickType
case class LimitClick(limit: LimitArea, target: LngLatLike) extends ClickType
case class LimitedFairwayClick(limit: LimitArea, area: FairwayArea, target: LngLatLike)
  extends ClickType

class MapMouseListener(
  map: MapboxMap,
  pathFinder: PathFinder,
  ais: AISRenderer,
  html: Popups,
  val log: BaseLogger = BaseLogger.console
) extends FrontKeys:
  val popup = MapboxPopup(PopupOptions())
  var isTrackHover: Boolean = false
  private var isPopupOpen: Boolean = false

  private def parseClick(e: MapMouseEvent): Option[Either[JsonError, ClickType]] =
    if isPopupOpen then
      popup.remove()
      isPopupOpen = false
      None
    else
      val features = map
        .queryRendered(e.point)
        .recover: err =>
          log.info(s"Failed to parse features '${err.error}' in '${err.json}'.")
          Nil
//      features foreach (f => log.info(s"$f"))
      val symbol: Option[Feature] = features.find: f =>
        f.geometry.typeName == PointGeometry.Key &&
          f.layer.exists(l => l.`type` == LayerType.Symbol || l.`type` == LayerType.Circle)
      symbol
        .map: symbolFeature =>
          val target =
            symbolFeature.geometry.coords.headOption.map(LngLatLike.apply).getOrElse(e.lngLat)
          val props = symbolFeature.props
          if symbolFeature.layer.exists(_.id == AISRenderer.AisVesselLayer) then
            // AIS
            validate[VesselProps](props).map(vp => VesselClick(vp, target))
          else if symbolFeature.layer.exists(_.id.startsWith(TrophyPrefix)) then
            validate[PointProps](props).map: tp =>
              TrophyClick(tp, target)
          else if symbolFeature.layer.exists(_.id.startsWith(DevicePrefix)) then
            validate[DeviceProps](props).map: tp =>
              DeviceClick(tp, target)
          else
            // Markers
            val normalSymbol = validate[MarineSymbol](props).map(m => SymbolClick(m, target))
            val minimalSymbol =
              validate[MinimalMarineSymbol](props).map(m => MinimalClick(m, target))
            normalSymbol.left.flatMap(_ => minimalSymbol)
        .orElse:
          if !isTrackHover then
            val limitAreas = features
              .flatMap(_.props.as[LimitArea].toOption)
              .toList
            val limitInfo = LimitArea
              .merge(limitAreas)
              .map(area => LimitClick(area, e.lngLat))
            val fairwayInfo =
              features
                .flatMap(_.props.as[FairwayInfo].toOption)
                .headOption
                .map(FairwayInfoClick(_, e.lngLat))
            val fairway = features
              .flatMap(f => f.props.as[FairwayArea].toOption)
              .headOption
              .map(FairwayClick(_, e.lngLat))
            val depth =
              features
                .flatMap(f => f.props.as[DepthArea].toOption)
                .headOption
                .map(DepthClick(_, e.lngLat))
            val combined = limitInfo.flatMap: l =>
              fairway.map(fc => LimitedFairwayClick(l.limit, fc.area, l.target))
            fairwayInfo
              .orElse(combined)
              .orElse(fairway)
              .orElse(limitInfo)
              .orElse(depth)
              .map(Right.apply)
          else None

  map.on(
    "click",
    (e: MapMouseEvent) =>
      if pathFinder.isEnabled then pathFinder.updatePath(e)
      else
        parseClick(e).foreach: result =>
          result
            .map:
              case DeviceClick(props, target) =>
                popup.show(html.device(props), target, map)
              case TrophyClick(props, target) =>
                popup.show(html.track(props), target, map)
              case VesselClick(boat, target) =>
                ais
                  .info(boat.mmsi)
                  .map { info =>
                    popup.show(html.ais(info), target, map)
                  }
                  .recover { err =>
                    log.info(s"Vessel info not available for '$boat'. $err.")
                  }
              case SymbolClick(marker, target) =>
                popup.show(html.mark(marker), target, map)
              case MinimalClick(marker, target) =>
                popup.show(html.minimalMark(marker), target, map)
              case FairwayClick(area, target) =>
                popup.show(html.fairway(area), target, map)
              case DepthClick(area, target) =>
                popup.show(html.depthArea(area), target, map)
              case FairwayInfoClick(info, target) =>
                popup.show(html.fairwayInfo(info), target, map)
              case LimitClick(limit, target) =>
                popup.show(html.limitArea(limit), target, map)
              case LimitedFairwayClick(limit, area, target) =>
                popup.show(html.limitedFairway(limit, area), target, map)
            .map(_ => isPopupOpen = true)
            .recover: err =>
              log.info(err.describe)
  )
  MapboxStyles.clickableLayers.foreach: id =>
    map.onHover(id)(
      in = _ => map.getCanvas().style.cursor = "pointer",
      out = _ => map.getCanvas().style.cursor = ""
    )
