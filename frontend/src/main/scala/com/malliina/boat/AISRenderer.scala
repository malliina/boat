package com.malliina.boat

import com.malliina.boat.AISRenderer.{AisVesselLayer, AisTrailLayer, MaxTrailLength}
import com.malliina.mapbox.MapboxMap
import play.api.libs.json.Json

object AISRenderer {
  val AisVesselLayer = "ais-vessels"
  val AisTrailLayer = "ais-vessels-trails"
  val MaxTrailLength = 1000

  def apply(map: MapboxMap) = new AISRenderer(map)
}

class AISRenderer(map: MapboxMap) extends GeoUtils with Parsing {
  // How much memory does this consume, assume 5000 Mmsis?
  private var vessels = Map.empty[Mmsi, List[VesselInfo]]

  def info(mmsi: Mmsi): Option[VesselInfo] =
    vessels.get(mmsi).flatMap(_.headOption)

  def locationData = FeatureCollection(
    vessels.values.flatMap(_.headOption).map { v =>
      val heading = v.heading.getOrElse(v.cog.toInt)
      val props = Json.obj(Mmsi.Key -> v.mmsi, VesselInfo.HeadingKey -> heading)
      Feature.point(v.coord, props.value.toMap)
    }.toList
  )

  def trailData = FeatureCollection(
    vessels.values.map(_.drop(1)).map { trail =>
      Feature.line(trail.map(_.coord))
    }.toList
  )

  def onAIS(messages: Seq[VesselInfo]): Unit = {
    val updated = messages.map { msg =>
      msg.mmsi -> (msg :: vessels.getOrElse(msg.mmsi, Nil)).take(MaxTrailLength)
    }.toMap
    vessels = vessels ++ updated
    val id = AisVesselLayer
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
    val trailId = AisTrailLayer
    val trailSrc = map.getSource(trailId)
    if (trailSrc.isEmpty) {
      val trailLayer = Layer(
        trailId,
        LineLayer,
        LayerSource(trailData),
        Option(LineLayout("round", "round")),
        Option(LinePaint("#000", 1, 1)),
        minzoom = Option(10)
      )
      map.putLayer(trailLayer)
    } else {
      trailSrc.foreach { geo =>
        geo.updateData(trailData)
      }
    }
  }
}
