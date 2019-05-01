package com.malliina.boat

import com.malliina.boat.Parsing.validate
import com.malliina.mapbox._
import com.malliina.util.EitherOps

sealed trait ClickType {
  def target: LngLatLike
}

case class VesselClick(props: VesselProps, target: LngLatLike) extends ClickType
case class SymbolClick(symbol: MarineSymbol, target: LngLatLike) extends ClickType
case class MinimalClick(symbol: MinimalMarineSymbol, target: LngLatLike) extends ClickType
case class FairwayClick(area: FairwayArea, target: LngLatLike) extends ClickType
case class FairwayInfoClick(info: FairwayInfo, target: LngLatLike) extends ClickType
case class DepthClick(area: DepthArea, target: LngLatLike) extends ClickType

object MapMouseListener {
  def apply(map: MapboxMap, ais: AISRenderer, html: Popups) = new MapMouseListener(map, ais, html)
}

class MapMouseListener(map: MapboxMap,
                       ais: AISRenderer,
                       html: Popups,
                       val log: BaseLogger = BaseLogger.console) {
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
      } else {
        // Markers
        validate[MarineSymbol](props)
          .map(m => SymbolClick(m, target))
          .left
          .flatMap(_ => validate[MinimalMarineSymbol](props).map(m => MinimalClick(m, target)))
      }
    }.orElse {
      markPopup.remove()
      if (!isTrackHover) {
        val fairwayInfo = features.flatMap(_.props.asOpt[FairwayInfo]).headOption.map(FairwayInfoClick(_, e.lngLat))
        val fairway = features
          .flatMap(f => f.props.asOpt[FairwayArea])
          .headOption
          .map(FairwayClick(_, e.lngLat))
        val depth =
          features.flatMap(f => f.props.asOpt[DepthArea]).headOption.map(DepthClick(_, e.lngLat))
        fairwayInfo.orElse(fairway.orElse(depth)).map(Right.apply)
      } else {
        None
      }
    }
  }

  map.on(
    "click",
    e => {
      parseClick(e).map {
        result =>
          result.map {
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
          }.recover { err =>
            log.info(err.describe)
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
