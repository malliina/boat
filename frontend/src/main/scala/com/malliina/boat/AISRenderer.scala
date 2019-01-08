package com.malliina.boat

import com.malliina.mapbox.MapboxMap

object AISRenderer {
  def apply(map: MapboxMap) = new AISRenderer(map)
}

class AISRenderer(map: MapboxMap) extends GeoUtils with Parsing {
  // If we want a trail, we can store a Seq[VesselLocation] instead
  //  val locations = Map.empty[Mmsi, VesselLocation]
  //  val metadatas = Map.empty[Mmsi, VesselMetadata]

  def onAIS(messages: VesselMessages): Unit = {
    messages.metas.foreach { meta => updateMetadata(meta) }
    messages.locations.foreach { loc => renderLocation(loc) }
  }

  def renderLocation(loc: VesselLocation): Unit = {
    //    val _ = locations.getOrElse(loc.mmsi, loc)
    val id = idFor(loc.mmsi)
    if (map.getSource(id).isEmpty) {
      map.putLayer(Layer(id, CircleLayer, LayerSource(pointFor(loc.coord)), None, Option(CirclePaint(10, "#007cbf"))))
    } else {
      map.getSource(id).foreach { geoJson =>
        geoJson.setData(toJson(pointFor(loc.coord)))
      }
    }
  }

  def updateMetadata(meta: VesselMetadata): Unit = {

  }

  def idFor(mmsi: Mmsi) = s"mmsi-$mmsi"
}
