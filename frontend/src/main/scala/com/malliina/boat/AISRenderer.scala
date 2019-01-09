package com.malliina.boat

import com.malliina.mapbox.MapboxMap
import play.api.libs.json.Json

object AISRenderer {
  val AisLayer = "ais-vessels"

  def apply(map: MapboxMap) = new AISRenderer(map)
}

class AISRenderer(map: MapboxMap) extends GeoUtils with Parsing {
  // If we want a trail, we can store a Seq[VesselInfo] instead
  private var vessels = Map.empty[Mmsi, VesselInfo]

  def info(mmsi: Mmsi): Option[VesselInfo] =
    vessels.get(mmsi)

  def locationData = FeatureCollection(
    vessels.values.map { v =>
      val heading = v.heading.getOrElse(v.cog.toInt)
      val props = Json.obj(Mmsi.Key -> v.mmsi, VesselInfo.HeadingKey -> heading)
      Feature.point(v.coord, props.value.toMap)
    }.toList
  )

  def onAIS(messages: Seq[VesselInfo]): Unit = {
    vessels = vessels ++ messages.map { m => m.mmsi -> m }.toMap
    val id = AISRenderer.AisLayer
    val src = map.getSource(id)
    if (src.isEmpty) {
      val shipLayer = Layer(
        id,
        SymbolLayer,
        LayerSource(locationData),
        Option(ImageLayout(boatIconId, `icon-size` = 1, Option(Seq("get", VesselInfo.HeadingKey)))),
        None
      )
      map.putLayer(shipLayer)
    } else {
      src.foreach { geo =>
        geo.updateData(locationData)
      }
    }
  }
}
