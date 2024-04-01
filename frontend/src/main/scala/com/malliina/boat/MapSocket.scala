package com.malliina.boat

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{Async, Temporal}
import cats.syntax.list.*
import cats.syntax.show.toShow
import com.malliina.boat.BoatFormats.*
import com.malliina.boat.FrontKeys.TrophyPrefix
import com.malliina.boat.SourceType.Vehicle
import com.malliina.geojson.{GeoLineString, GeoPoint}
import com.malliina.mapbox.*
import com.malliina.measure.SpeedM
import com.malliina.turf.nearestPointOnLine
import com.malliina.values.ErrorMessage
import fs2.concurrent.Topic
import org.scalajs.dom.MessageEvent

import scala.scalajs.js

case class NearestResult[T](result: T, distance: Double)

case class TrackIds(id: TrackId, track: TrackName, source: DeviceId, start: Timing):
  def trail = s"track-$track"
  def hoverable = s"$trail-thick"
  def trophy = s"$TrophyPrefix-$track"
  def point = s"boat-$track"
  def all = Seq(trail, hoverable, trophy, point)

class MapSocket[F[_]: Temporal: Async](
  val map: MapboxMap,
  pathFinder: PathFinder[F],
  mode: MapMode,
  messages: Topic[F, MessageEvent],
  dispatcher: Dispatcher[F],
  lang: Lang,
  log: BaseLogger
) extends BaseFront
  with GeoUtils:
  val F = Async[F]
  val events = Events(messages)
  private var socket: Option[BoatSocket[F]] = None

  private val trackLang = lang.track
  private val emptyTrack = lineForTrack(Nil)
  private val trackPopup = MapboxPopup(PopupOptions())
  private val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))
  private val ais = AISRenderer(map)
  private val html = Popups(lang)
  private val popups = MapMouseListener[F](map, pathFinder, ais, html)

  private var mapMode: MapMode = mode

  /** These two maps are two representations of the same data. One in GeoJSON the other in Scala
    * case classes.
    */
  private var boats = Map.empty[TrackIds, FeatureCollection]
  private var trails = Map.empty[TrackId, Seq[TimedCoord]]
  private var hovering = Set.empty[TrackIds]

  private def spinner = elemGet(LoadingSpinnerId)

  def reconnect(track: PathState, sample: Option[Int]): Unit =
    socket.foreach(_.close())
    clear()
    val path = s"/ws/updates${BoatSocket.query(track, sample)}"
    val s = new BoatSocket(path, messages, dispatcher)
    socket = Option(s)

  private val showSpinner: fs2.Stream[F, Boolean] = events.frontEvents
    .collect:
      case CoordsBatch(events) => false
      case CoordsEvent(_, _)   => false
      case LoadingEvent(_)     => true
      case NoDataEvent(_)      => false
    .changes
  val spinnerListener = showSpinner.tap: show =>
    if show then spinner.show() else spinner.hide()
  val coordsListener = events.coordEvents.tap: event =>
    onCoords(event)
  val aisListener = events.aisEvents.tap: messages =>
    ais.onAIS(messages)
  val task = spinnerListener.concurrently(coordsListener).concurrently(aisListener)

  /** Colors the track by speed.
    *
    * @see
    *   https://docs.mapbox.com/mapbox-gl-js/example/heatmap-layer/
    * @param id
    *   layer ID
    */
  private def lineLayer(id: String, opacity: Double = 1d) =
    trackLineLayer(id, LinePaint(LinePaint.blackColor, 1, opacity))
//  trackLineLayer(id, LinePaint(PropertyValue.Custom(Styles.colorBySpeed), 1, 1))

  private def trackLineLayer(id: String, paint: LinePaint): Layer =
    Layer.line(id, emptyTrack, paint, minzoom = None)

  private def onCoords(event: CoordsEvent): Unit =
    val from = event.from
    val isBoat = from.sourceType == SourceType.Boat
    val trackId = from.track
    val coordsInfo = event.coords
    val coords = coordsInfo.map(_.coord)
    val boat = from.boatName
    val ids = TrackIds(from.track, from.trackName, from.boat, from.times.start)
    val track = ids.trail
    val trophyLayerId = ids.trophy
    val hoverableTrack = ids.hoverable
    val point = ids.point
    val oldTrack: FeatureCollection = boats.getOrElse(ids, emptyTrack)
    val latestMeasurement =
      for
        latestFeature <- oldTrack.features.lastOption
        latestCoord <- latestFeature.geometry.coords.headOption
        speedProp <- latestFeature.properties.get(TimedCoord.SpeedKey)
        speed <- speedProp.as[SpeedM].toOption
      yield SimpleCoord(latestCoord, speed)
    val newTrack: FeatureCollection =
      oldTrack
        .copy(features = oldTrack.features ++ speedFeatures(latestMeasurement.toSeq ++ coordsInfo))
    boats = boats.updated(ids, newTrack)
    trails = trails.updated(trackId, trails.getOrElse(trackId, Nil) ++ coordsInfo)
    // adds layer if not already added
    if map.findSource(track).isEmpty then
      // All but the latest trails have lower opacity. Older = less prominent.
      boats.keys
        .map(_.trail)
        .filter(map.hasLayer)
        .foreach: layerId =>
          map.setPaintProperty(layerId, "line-opacity", 0.4)
      // Only shows the trophy and boat/car icon of the latest track
      boats.keys
        .flatMap(k => Seq(k.trophy, k.point))
        .filter(map.hasLayer)
        .foreach(id => map.removeLayer(id))
      log.debug(s"Crafting new track for boat '$boat'...")
      map.putLayer(lineLayer(track))
      // adds a thicker, transparent trail on top of the visible one, which represents the mouse-hoverable area
      map.putLayer(trackLineLayer(hoverableTrack, LinePaint(LinePaint.blackColor, 5, 0)))
      coords.lastOption.foreach: coord =>
        // adds boat icon
        val layer = if isBoat then boatSymbolLayer(point, coord) else carSymbolLayer(point, coord)
        map.putLayer(layer)
        map.onHover(point)(
          in =>
            map.getCanvas().style.cursor = "pointer"
            trackPopup.remove()
            boatPopup.showText(from.boatName.show, in.lngLat, map)
          ,
          _ =>
            map.getCanvas().style.cursor = ""
            boatPopup.remove()
        )
      // adds trophy icon
      map.putLayer(trophySymbolLayer(trophyLayerId, from.topPoint.coord))
      map.onHover(trophyLayerId)(
        _ => map.getCanvas().style.cursor = "pointer",
        _ => map.getCanvas().style.cursor = ""
      )

      if !hovering.exists(_.hoverable == hoverableTrack) then
        log.info(s"Installing hover for $hoverableTrack")
        val hoverIn: js.Function1[MapMouseEvent, Unit] = in =>
          val isOnBoatSymbol = map
            .queryRendered(in.point, QueryOptions.all)
            .getOrElse(Nil)
            .exists(_.layer.exists(_.id == point))
          if !isOnBoatSymbol then
            val hover = in.lngLat
            val op = for
              hoverCoord <- Coord.build(hover.lng, hover.lat)
              trailCoords <- trails
                .getOrElse(trackId, Nil)
                .toList
                .toNel
                .toRight(ErrorMessage(s"No coords for $trackId."))
            yield nearest(hoverCoord, trailCoords)(_.coord).map: near =>
              map.getCanvas().style.cursor = "pointer"
              popups.isTrackHover = true
              trackPopup.show(html.track(PointProps(near.result, from)), in.lngLat, map)
            op.fold(err => log.info(err.message), identity)
        val hoverOut: js.Function1[MapMouseEvent, Unit] = _ =>
          map.getCanvas().style.cursor = ""
          popups.isTrackHover = false
          trackPopup.remove()
        map.onHover(hoverableTrack)(hoverIn, hoverOut)
      else log.info(s"Already tracking hovering over $hoverableTrack.")
    // updates the source icon
    map
      .findSource(point)
      .foreach: geoJson =>
        coords.lastOption.foreach: coord =>
          // updates placement
          geoJson.updateData(pointFor(coord))
          // updates bearing
          newTrack.features.flatMap(_.geometry.coords).takeRight(2).toList match
            case prev :: last :: _ =>
              val spin = bearing(prev, last).toInt
              // The car SVG icon is pointing left, so this rotates by 90 degrees
              val rotation = if isBoat then spin else (spin + 90) % 360
              map.setLayoutProperty(point, ImageLayout.IconRotate, rotation)
            case _ =>
              ()
    // updates the trail
    map
      .findSource(track)
      .foreach: geoJson =>
        geoJson.updateData(newTrack)
    map
      .findSource(hoverableTrack)
      .foreach: geoJson =>
        geoJson.updateData(newTrack)
    // updates the trophy icon
    val topPoint = from.topPoint
    map
      .findSource(trophyLayerId)
      .foreach: geoJson =>
        geoJson.updateData(pointForProps(topPoint.coord, PointProps(topPoint, from)))
    elem(TitleId).foreach: e =>
      from.trackTitle.foreach: title =>
        e.show()
        e.innerHTML = title.show
    elem(DistanceId).foreach: e =>
      e.show()
      e.innerHTML = s"${formatDistance(from.distanceMeters)} km"
    elem(TopSpeedId).foreach: e =>
      from.topSpeed.foreach: top =>
        e.show()
        val formatted = from.sourceType match
          case Vehicle => s"${formatKph(top)} km/h"
          case _       => s"${formatKnots(top)} kn"
        e.innerHTML = s"${trackLang.top} $formatted"
    if from.sourceType == SourceType.Boat then
      elem(WaterTempId).foreach: e =>
        coordsInfo.lastOption
          .map(_.waterTemp)
          .foreach: temp =>
            e.show()
            e.innerHTML = s"${trackLang.water} ${formatTemp(temp)} ℃"
    anchor(FullLinkId).foreach: e =>
      e.show()
      e.href = s"/tracks/${from.trackName}/full"
    anchor(GraphLinkId).foreach: e =>
      e.show()
      e.href = s"/tracks/${from.trackName}/chart"
    elem(EditTitleId).foreach: e =>
      e.show()
    if boats.keySet.size == 1 then
      elem(DurationId).foreach: e =>
        e.show()
        e.innerHTML = s"${trackLang.duration} ${formatDuration(from.duration)}"
    // updates the map position, zoom to reflect the updated track(s)
    mapMode match
      case MapMode.Fit =>
        ()
      case MapMode.Follow =>
        if boats.keySet.size == 1 then
          coords.lastOption.foreach: coord =>
            map.easeTo(EaseOptions(coord))
        else
          // does not follow if more than one boats are online, since it's not clear what to follow
          mapMode = MapMode.Stay
      case MapMode.Stay =>
        ()
    hovering = hovering ++ Set(ids)

  private val _ = MapboxPopup(PopupOptions(className = Option("popup-device")))

  def fitToMap(): Unit =
    if mapMode == MapMode.Fit then
      val eligibleTrails = trails.filter((_, cs) => cs.size > 2)
      val lengths = eligibleTrails.map((_, cs) => cs.size).mkString(", ")
      log.info(s"Fitting to map from ${eligibleTrails.size} trails of lengths $lengths...")
      val trail = eligibleTrails.values.flatten.map(_.coord)
      trail.headOption.foreach: head =>
        val init = LngLatBounds(head)
        val bs: LngLatBounds = trail
          .drop(1)
          .foldLeft(init): (bounds, c) =>
            bounds.extend(LngLat(c))
        try map.fitBounds(bs, SimplePaddingOptions(60))
        catch
          case e: Exception =>
            val sw = bs.getSouthWest()
            val nw = bs.getNorthWest()
            val ne = bs.getNorthEast()
            val se = bs.getSouthEast()
            log.error(s"Unable to fit using $sw $nw $ne $se", e)
    else log.info(s"Not fitting, map mode is $mapMode")

  private def nearest[T](fromCoord: Coord, on: NonEmptyList[T])(
    c: T => Coord
  ): Either[ErrorMessage, NearestResult[T]] =
    val coords = on.map(c)
    val all = GeoLineString(coords.toList)
    log.info(s"Searching nearest update among ${coords.size} coords...")
    val turfPoint = GeoPoint(fromCoord)
    val nearestResult = nearestPointOnLine(all, turfPoint)
    val str = scalajs.js.JSON.stringify(nearestResult)
    log.info(s"Nearest json $str")
    val idx = nearestResult.properties.index
    if on.length > idx then Right(NearestResult(on.toList(idx), nearestResult.properties.dist))
    else Left(ErrorMessage(s"No trail at $fromCoord."))

  // https://www.movable-type.co.uk/scripts/latlong.html
  private def bearing(from: Coord, to: Coord): Double =
    val dLon = to.lng.lng - from.lng.lng
    val y = Math.sin(dLon) * Math.cos(to.lat.lat)
    val x = Math.cos(from.lat.lat) * Math.sin(to.lat.lat) - Math.sin(from.lat.lat) * Math.cos(
      to.lat.lat
    ) * Math.cos(dLon)
    val brng = toDeg(Math.atan2(y, x))
    360 - ((brng + 360) % 360)

  private def toDeg(rad: Double) = rad * 180 / Math.PI

  private def boatSymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(boatIconId, `icon-size` = 0.7))
  private def carSymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(carIconId, `icon-size` = 0.5))
  private def trophySymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(trophyIconId, `icon-size` = 1))

  def clear(): Unit =
    for
      ids <- boats.keys
      id <- ids.all
    yield remove(id)
    boats = Map.empty
    trails = Map.empty
    Seq(
      TitleId,
      DistanceId,
      TopSpeedId,
      WaterTempId,
      FullLinkId,
      GraphLinkId,
      EditTitleId,
      DurationId
    ).foreach: id =>
      elem(id).foreach(_.hide())

  def remove(id: String): Unit =
    if map.hasLayer(id) then map.removeLayer(id)
    if map.hasSource(id) then map.removeSource(id)
