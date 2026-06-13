package com.malliina.boat

import com.malliina.geojson.PointGeometry
import com.malliina.json.JsonError
import com.malliina.json.Parsing.validate
import com.malliina.mapbox.*
import com.malliina.util.recover

enum ClickType(val lngLat: LngLatLike):
  case VesselClick(props: VesselProps, target: LngLatLike) extends ClickType(target)
  case TrophyClick(trophy: PointProps, target: LngLatLike) extends ClickType(target)
  case DeviceClick(device: DeviceProps, target: LngLatLike) extends ClickType(target)
  case SymbolClick(symbol: MarineSymbol, target: LngLatLike) extends ClickType(target)
  case MinimalClick(symbol: MinimalMarineSymbol, target: LngLatLike) extends ClickType(target)
  case FairwayClick(area: FairwayArea, target: LngLatLike) extends ClickType(target)
  case FairwayInfoClick(info: FairwayInfo, target: LngLatLike) extends ClickType(target)
  case DepthClick(area: DepthArea, target: LngLatLike) extends ClickType(target)
  case LimitClick(limit: LimitArea, target: LngLatLike) extends ClickType(target)
  case LimitedFairwayClick(limit: LimitArea, area: FairwayArea, target: LngLatLike)
    extends ClickType(target)

class MapMouseListener[F[_]](
  map: MapboxMap,
  pathFinder: PathFinder[F],
  ais: AISRenderer,
  vesselSearch: VesselSearch[F],
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
      val symbol: Option[LayerFeature] = features.find: f =>
        f.geometry.typeName == PointGeometry.Key &&
          f.layer.exists(l => l.`type` == LayerType.Symbol || l.`type` == LayerType.Circle)
      symbol
        .map: symbolFeature =>
          val target =
            symbolFeature.geometry.coords.headOption.map(LngLatLike.apply).getOrElse(e.lngLat)
          val props = symbolFeature.props
          if symbolFeature.layer.exists: id =>
              vesselSearch.symbolIds.contains(id.id) || id.id == AISRenderer.AisVesselLayer
          then
            // AIS
            validate[VesselProps](props).map(vp => ClickType.VesselClick(vp, target))
          else if symbolFeature.layer.exists(_.id.startsWith(TrophyPrefix)) then
            validate[PointProps](props).map: tp =>
              ClickType.TrophyClick(tp, target)
          else if symbolFeature.layer.exists(_.id.startsWith(DevicePrefix)) then
            validate[DeviceProps](props).map: tp =>
              ClickType.DeviceClick(tp, target)
          else
            // Markers
            val normalSymbol =
              validate[MarineSymbol](props).map(m => ClickType.SymbolClick(m, target))
            val minimalSymbol =
              validate[MinimalMarineSymbol](props).map(m => ClickType.MinimalClick(m, target))
            normalSymbol.left.flatMap(_ => minimalSymbol)
        .orElse:
          if !isTrackHover then
            val limitAreas = features
              .flatMap(_.props.as[LimitArea].toOption)
              .toList
            val limitInfo: Option[ClickType.LimitClick] = LimitArea
              .merge(limitAreas)
              .map(area => ClickType.LimitClick(area, e.lngLat))
            val fairwayInfo =
              features
                .flatMap(_.props.as[FairwayInfo].toOption)
                .headOption
                .map(ClickType.FairwayInfoClick(_, e.lngLat))
            val fairway: Option[ClickType.FairwayClick] = features
              .flatMap(f => f.props.as[FairwayArea].toOption)
              .headOption
              .map(ClickType.FairwayClick(_, e.lngLat))
            val depth =
              features
                .flatMap(f => f.props.as[DepthArea].toOption)
                .headOption
                .map(ClickType.DepthClick(_, e.lngLat))
            val combined = limitInfo.flatMap: l =>
              fairway.map(fc => ClickType.LimitedFairwayClick(l.limit, fc.area, l.target))
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
              case ClickType.DeviceClick(props, target) =>
                popup.show(html.device(props), target, map)
              case ClickType.TrophyClick(props, target) =>
                popup.show(html.track(props), target, map)
              case ClickType.VesselClick(boat, target) =>
                ais
                  .info(boat.mmsi)
                  .map: info =>
                    popup.show(html.ais(info), target, map)
                  .orElse:
                    vesselSearch
                      .info(boat.mmsi)
                      .map: info =>
                        popup.show(html.aisSimple(info), target, map)
                  .recover: err =>
                    log.info(s"Vessel info not available for '$boat'. $err.")
              case ClickType.SymbolClick(marker, target) =>
                popup.show(html.mark(marker), target, map)
              case ClickType.MinimalClick(marker, target) =>
                popup.show(html.minimalMark(marker), target, map)
              case ClickType.FairwayClick(area, target) =>
                popup.show(html.fairway(area), target, map)
              case ClickType.DepthClick(area, target) =>
                popup.show(html.depthArea(area), target, map)
              case ClickType.FairwayInfoClick(info, target) =>
                popup.show(html.fairwayInfo(info), target, map)
              case ClickType.LimitClick(limit, target) =>
                popup.show(html.limitArea(limit), target, map)
              case ClickType.LimitedFairwayClick(limit, area, target) =>
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
