package com.malliina.boat

import com.malliina.boat.Parsing.validate
import com.malliina.mapbox.{LngLat, MapboxMap, MapboxPopup, PopupOptions}
import com.malliina.values.ErrorMessage
import com.malliina.util.EitherOps

object MapMouseListener {
  def apply(map: MapboxMap, ais: AISRenderer, html: Popups) = new MapMouseListener(map, ais, html)
}

class MapMouseListener(map: MapboxMap,
                       ais: AISRenderer,
                       html: Popups, val log: BaseLogger = BaseLogger.console) {
  val markPopup = MapboxPopup(PopupOptions())
  var isTrackHover: Boolean = false

  map.on("click", e => {
    val features = map.queryRendered(e.point).recover { err =>
      log.info(s"Failed to parse features '${err.error}' in '${err.json}'.")
      Nil
    }
    val symbol = features.find { f =>
      f.geometry.typeName == PointGeometry.Key &&
        f.layer.exists(l => l.`type` == LayerType.Symbol || l.`type` == LayerType.Circle)
    }
    val vessel = symbol.filter(_.layer.exists(_.id == AISRenderer.AisVesselLayer))
    vessel.map { feature =>
      val maybeInfo = for {
        props <- validate[VesselProps](feature.props).left.map(err => ErrorMessage(s"JSON error. $err"))
        info <- ais.info(props.mmsi)
      } yield info
      maybeInfo.map { vessel =>
        //        log.info(s"Selected vessel $vessel.")
        val target = feature.geometry.coords.headOption.map(LngLat.apply).getOrElse(e.lngLat)
        markPopup.show(html.ais(vessel), target, map)
      }.recover { err =>
        log.info(s"Vessel info not available for '${feature.props}'. $err.")
      }
    }.getOrElse {
      symbol.fold(markPopup.remove()) { feature =>
        val symbol = validate[MarineSymbol](feature.props)
        val target = feature.geometry.coords.headOption.map(LngLat.apply).getOrElse(e.lngLat)
        symbol.map { ok =>
          markPopup.show(html.mark(ok), target, map)
        }.recoverWith { _ =>
          validate[MinimalMarineSymbol](feature.props).map { ok =>
            markPopup.show(html.minimalMark(ok), target, map)
          }
        }.recover { err =>
          log.info(err.describe)
        }
      }
    }
    if (symbol.isEmpty && vessel.isEmpty && !isTrackHover) {
      val maybeFairway = features.flatMap(f => f.props.asOpt[FairwayArea]).headOption
      maybeFairway.foreach { fairway =>
        markPopup.show(html.fairway(fairway), e.lngLat, map)
      }
      if (maybeFairway.isEmpty) {
        features.flatMap(f => f.props.asOpt[DepthArea]).headOption.foreach { depthArea =>
          markPopup.show(html.depthArea(depthArea), e.lngLat, map)
        }
      }
    }
  })
  MapboxStyles.clickableLayers.foreach { id =>
    map.onHover(id)(
      in = _ => map.getCanvas().style.cursor = "pointer",
      out = _ => map.getCanvas().style.cursor = ""
    )
  }
}
