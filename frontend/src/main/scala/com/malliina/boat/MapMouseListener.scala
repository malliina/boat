package com.malliina.boat

import com.malliina.boat.Parsing.validate
import com.malliina.mapbox._
import com.malliina.util.EitherOps

sealed trait ClickType {
  def target: LngLatLike
}

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

object MapMouseListener {
  def apply(map: MapboxMap, pathFinder: PathFinder, ais: AISRenderer, html: Popups) =
    new MapMouseListener(map, pathFinder, ais, html)
}

class MapMouseListener(
  map: MapboxMap,
  pathFinder: PathFinder,
  ais: AISRenderer,
  html: Popups,
  val log: BaseLogger = BaseLogger.console
) extends FrontKeys {
  val markPopup = MapboxPopup(PopupOptions())
  var isTrackHover: Boolean = false

  def parseClick(e: MapMouseEvent): Option[Either[JsonError, ClickType]] = {
    val features = map.queryRendered(e.point).recover { err =>
      log.info(s"Failed to parse features '${err.error}' in '${err.json}'.")
      Nil
    }
    val symbol: Option[Feature] = features.find { f =>
      f.geometry.typeName == PointGeometry.Key &&
      f.layer.exists(l => l.`type` == LayerType.Symbol || l.`type` == LayerType.Circle)
    }
    symbol.map { symbolFeature =>
      val target =
        symbolFeature.geometry.coords.headOption.map(LngLatLike.apply).getOrElse(e.lngLat)
      val props = symbolFeature.props
      if (symbolFeature.layer.exists(_.id == AISRenderer.AisVesselLayer)) {
        // AIS
        validate[VesselProps](props).map(vp => VesselClick(vp, target))
      } else if (symbolFeature.layer.exists(_.id.startsWith(TrophyPrefix))) {
        validate[PointProps](props).map { tp =>
          TrophyClick(tp, target)
        }
      } else if (symbolFeature.layer.exists(_.id.startsWith(DevicePrefix))) {
        validate[DeviceProps](props).map { tp =>
          DeviceClick(tp, target)
        }
      } else {
        // Markers
        val normalSymbol = validate[MarineSymbol](props).map(m => SymbolClick(m, target))
        val minimalSymbol = validate[MinimalMarineSymbol](props).map(m => MinimalClick(m, target))
        normalSymbol.left.flatMap(_ => minimalSymbol)
      }
    }.orElse {
      if (!isTrackHover) {
        val limitInfo =
          features.flatMap(_.props.asOpt[LimitArea]).headOption.map(LimitClick(_, e.lngLat))
        val fairwayInfo =
          features.flatMap(_.props.asOpt[FairwayInfo]).headOption.map(FairwayInfoClick(_, e.lngLat))
        val fairway = features
          .flatMap(f => f.props.asOpt[FairwayArea])
          .headOption
          .map(FairwayClick(_, e.lngLat))
        val depth =
          features.flatMap(f => f.props.asOpt[DepthArea]).headOption.map(DepthClick(_, e.lngLat))
        val combined = limitInfo.flatMap { l =>
          fairway.map(fc => LimitedFairwayClick(l.limit, fc.area, l.target))
        }
        fairwayInfo
          .orElse(combined)
          .orElse(fairway)
          .orElse(limitInfo)
          .orElse(depth)
          .map(Right.apply)
      } else {
        None
      }
    }
  }

  map.on(
    "click",
    (e: MapMouseEvent) => {
      if (pathFinder.isEnabled) {
        pathFinder.updatePath(e)
      } else {
        parseClick(e).map {
          result =>
            result.map {
              case DeviceClick(props, target) =>
                markPopup.show(html.device(props), target, map)
              case TrophyClick(props, target) =>
                markPopup.show(html.track(props), target, map)
              case VesselClick(boat, target) =>
                ais
                  .info(boat.mmsi)
                  .map { info =>
                    markPopup.show(html.ais(info), target, map)
                  }
                  .recover { err =>
                    log.info(s"Vessel info not available for '$boat'. $err.")
                  }
              case SymbolClick(marker, target) =>
                markPopup.show(html.mark(marker), target, map)
              case MinimalClick(marker, target) =>
                markPopup.show(html.minimalMark(marker), target, map)
              case FairwayClick(area, target) =>
                markPopup.show(html.fairway(area), target, map)
              case DepthClick(area, target) =>
                markPopup.show(html.depthArea(area), target, map)
              case FairwayInfoClick(info, target) =>
                markPopup.show(html.fairwayInfo(info), target, map)
              case LimitClick(limit, target) =>
                markPopup.show(html.limitArea(limit), target, map)
              case LimitedFairwayClick(limit, area, target) =>
                markPopup.show(html.limitedFairway(limit, area), target, map)
            }.recover { err =>
              log.info(err.describe)
            }
        }
      }

    }
  )
  MapboxStyles.clickableLayers.foreach { id =>
    map.onHover(id)(
      in = _ => map.getCanvas().style.cursor = "pointer",
      out = _ => map.getCanvas().style.cursor = ""
    )
  }
}
