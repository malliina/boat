package com.malliina.boat

import com.malliina.boat.AISRenderer.{AisTrailLayer, AisVesselLayer, MaxTrailLength}
import com.malliina.mapbox.{MapboxMap, MapboxPopup, PopupOptions, QueryOptions}
import com.malliina.values.ErrorMessage

object AISRenderer {
  val AisTrailLayer = MapboxStyles.AisTrailLayer
  val AisVesselLayer = MapboxStyles.AisVesselLayer

  val MaxTrailLength = 100

  def apply(map: MapboxMap): AISRenderer = new AISRenderer(map)
}

class AISRenderer(val map: MapboxMap, val log: BaseLogger = BaseLogger.console)
    extends GeoUtils
    with Parsing {
  // How much memory does this consume, assume 5000 Mmsis?
  private var vessels = Map.empty[Mmsi, List[VesselInfo]]

  val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))

  def info(mmsi: Mmsi): Either[ErrorMessage, VesselInfo] =
    vessels.get(mmsi).flatMap(_.headOption).toRight(ErrorMessage(s"MMSI not found: '$mmsi'."))

  def locationData = FeatureCollection(
    vessels.values
      .flatMap(_.headOption)
      .map { v =>
        val heading = v.heading.getOrElse(v.cog.toInt)
//      log.info(s"Heading $heading bearing ${map.bearing.toInt} rotation $bearing")
        Feature.point(v.coord, VesselProps(v.mmsi, v.name, heading))
      }
      .toList
  )

  def trailData = FeatureCollection(
    vessels.values
      .map(_.drop(1))
      .map { trail =>
        Feature.line(trail.map(_.coord))
      }
      .toList
  )

  def onAIS(messages: Seq[VesselInfo]): Unit = {
    val updated = messages.map { msg =>
      msg.mmsi -> (msg :: vessels.getOrElse(msg.mmsi, Nil)).take(MaxTrailLength)
    }.toMap
    vessels = vessels ++ updated
    val iconUpdateOutcome = updateOrSet(
      Layer.symbol(
        AisVesselLayer,
        locationData,
        ImageLayout(boatIconId, `icon-size` = 1, Option(Seq("get", VesselInfo.HeadingKey)))
      )
    )
    if (iconUpdateOutcome == Outcome.Added) {
      initHover(AisVesselLayer)
    }
    updateOrSet(
      Layer.line(AisTrailLayer, trailData, minzoom = Option(10))
    )
  }

  def initHover(id: String): Unit = map.onHover(id)(
    in => {
      map
        .queryRendered(in.point, QueryOptions.layer(id))
        .map { fs =>
          fs.flatMap(_.props.asOpt[VesselProps]).headOption.foreach { vessel =>
            boatPopup.showText(vessel.name.name, in.lngLat, map)
          }
        }
        .left
        .map { err =>
          log.info(s"JSON error '$err'.")
        }
    },
    _ => boatPopup.remove()
  )
}
