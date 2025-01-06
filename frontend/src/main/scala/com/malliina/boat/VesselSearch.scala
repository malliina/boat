package com.malliina.boat

import cats.effect.Sync
import com.malliina.mapbox.{LngLat, MapboxMap, MapboxPopup, PopupOptions}
import com.malliina.values.ErrorMessage
import fs2.Stream
import cats.syntax.list.*

class VesselSearch[F[_]: Sync](
  vessels: Stream[F, Seq[VesselTrail]],
  html: Popups,
  val map: MapboxMap
) extends GeoUtils(map, BaseLogger.console):
  private var symbolLayerIds: Set[String] = Set.empty

  private var storage = Map.empty[Mmsi, VesselTrail]
  private val trailPopup = MapboxPopup(PopupOptions())

  def symbolIds = symbolLayerIds
  def info(mmsi: Mmsi) = storage.get(mmsi).toRight(ErrorMessage("MMSI not found: '$mmsi'."))

  val task = vessels.tap: event =>
    onVessels(event)

  private def onVessels(vessels: Seq[VesselTrail]) =
    storage = storage ++ vessels.map(v => v.mmsi -> v).toMap
    vessels.map: trail =>
      val mmsi = trail.mmsi
      val prefix = s"vessel-$mmsi"
      val ups = trail.updates
      val coords = trail.updates.map(_.coord)
      val feature = Feature.line(coords)
      val layer = Layer.line(s"$prefix-trail", FeatureCollection(Seq(feature)))
      val lineOutcome = updateOrSet(layer)
      val hoverableLayer = Layer.line(
        s"$prefix-hoverable",
        FeatureCollection(Seq(feature)),
        LinePaint(LinePaint.blackColor, `line-width` = 5, `line-opacity` = 0)
      )
      val hoverOutcome = updateOrSet(hoverableLayer)
      if hoverOutcome == Outcome.Added then
        map.onHoverCursorPointer(layer.id)
        map.onHoverEnter(hoverableLayer.id)(
          in =>
            val hover = in.lngLat
            for
              coord <- Coord.build(hover.lng, hover.lat)
              trail <- storage.get(mmsi).toRight(s"Trail not found for vessel '$mmsi'.")
              updates <- trail.updates.toList.toNel
                .toRight(ErrorMessage(s"No coords for vessel '$mmsi'."))
            yield nearest(coord, updates)(_.coord).map: near =>
              trailPopup.show(
                html.aisSimple(trail.copy(updates = List(near.result))),
                LngLat(near.result.coord),
                map
              )
          ,
          out => trailPopup.remove()
        )
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
          map.onHoverCursorPointer(symbolLayer.id)
        fitTo(coords)
