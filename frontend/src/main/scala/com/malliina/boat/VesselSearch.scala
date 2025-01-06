package com.malliina.boat

import cats.effect.Sync
import com.malliina.mapbox.MapboxMap
import com.malliina.values.ErrorMessage
import fs2.Stream

class VesselSearch[F[_]: Sync](vessels: Stream[F, Seq[VesselTrail]], val map: MapboxMap)
  extends GeoUtils:
  private var symbolLayerIds: Set[String] = Set.empty

  private var storage = Map.empty[Mmsi, VesselTrail]

  def symbolIds = symbolLayerIds
  def info(mmsi: Mmsi) = storage.get(mmsi).toRight(ErrorMessage("MMSI not found: '$mmsi'."))

  val task = vessels.tap: event =>
    onVessels(event)

  private def onVessels(vessels: Seq[VesselTrail]) =
    storage = storage ++ vessels.map(v => v.mmsi -> v).toMap
    vessels.map: trail =>
      val prefix = s"vessel-${trail.mmsi}"
      val ups = trail.updates
      val coords = trail.updates.map(_.coord)
      val feature = Feature.line(coords)
      val layer = Layer.line(s"$prefix-trail", FeatureCollection(Seq(feature)))
      updateOrSet(layer)
      ups.headOption.foreach: latestPoint =>
        val latestCoord = latestPoint.coord
        val feature = Feature.point(
          latestCoord,
          VesselProps(trail.mmsi, trail.name, latestPoint.heading.getOrElse(0))
        )
        val symbolLayer = Layer.symbol(
          s"$prefix-icon",
          FeatureCollection(Seq(feature)),
          ImageLayout(
            boatIconId,
            `icon-size` = 1,
            `icon-rotate` = Option(Seq("get", VesselInfo.HeadingKey))
          )
        )
        val outcome = updateOrSet(symbolLayer)
        if outcome == Outcome.Added then
          symbolLayerIds += symbolLayer.id
          map.onHover(symbolLayer.id)(
            in => map.getCanvas().style.cursor = "pointer",
            out => map.getCanvas().style.cursor = ""
          )
        fitTo(coords)
