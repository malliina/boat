package com.malliina.boat

import cats.effect.std.Dispatcher
import cats.effect.Async
import cats.syntax.list.*
import cats.syntax.show.toShow
import com.malliina.boat.BoatFormats.*
import com.malliina.boat.FrontKeys.TrophyPrefix
import com.malliina.boat.SourceType.Vehicle
import com.malliina.mapbox.*
import com.malliina.measure.{DistanceIntM, DistanceM, SpeedM}
import com.malliina.values.ErrorMessage
import fs2.concurrent.Topic

import scala.concurrent.duration.Duration
import scala.scalajs.js

case class NearestResult[T](result: T, distance: Double)

case class TrackIds(id: TrackId, track: TrackName, source: DeviceId, start: Timing):
  def trail = s"track-$track"
  def hoverable = s"$trail-thick"
  def trophy = s"$TrophyPrefix-$track"
  def point = s"boat-$track"
  def all = Seq(trail, hoverable, trophy, point)

class MapSocket[F[_]: Async](
  val map: MapboxMap,
  pathFinder: PathFinder[F],
  mode: MapMode,
  messages: Topic[F, WebSocketEvent],
  dispatcher: Dispatcher[F],
  lang: Lang,
  log: BaseLogger
) extends BaseFront:
  val utils = GeoUtils(map, log)
  import utils.*
  val F = Async[F]
  val events = Events(messages)
  private var socket: Option[BoatSocket[F]] = None

  private val trackLang = lang.track
  private val emptyTrack = lineForTrack(Nil)
  private val trackPopup = MapboxPopup(PopupOptions())
  private val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))
  private val html = Popups(lang)
  private val ais = AISRenderer(map)
  private val vesselSearch = VesselSearch(events.vesselEvents, html, map)
  private val popups = MapMouseListener[F](map, pathFinder, ais, vesselSearch, html)

  private var mapMode: MapMode = mode

  /** These two maps are two representations of the same data. One in GeoJSON the other in Scala
    * case classes.
    */
  private var boats = Map.empty[TrackIds, FeatureCollection]
  private var trails = Map.empty[TrackId, CoordsEvent]
  private def froms = trails.values.map(_.from).toList
  private var hovering = Set.empty[TrackIds]

  private def spinner = elemGet(LoadingSpinnerId)

  def reconnect(track: PathState, sample: Option[Int]): Unit =
    socket.foreach(_.close())
    clear()
    val s = BoatSocket(BoatSocket.uri(track, sample), messages, dispatcher)
    socket = Option(s)

  private val showSpinner: fs2.Stream[F, Boolean] = events.frontEvents
    .collect:
      case CoordsBatch(events)  => false
      case CoordsEvent(_, _)    => false
      case VesselTrailsEvent(_) => false
      case LoadingEvent(_)      => true
      case NoDataEvent(_)       => false
    .changes
  private val spinnerListener = showSpinner.tap: show =>
    if show then spinner.show() else spinner.hide()
  private val coordsListener = events.coordEvents.tap: event =>
    onCoords(event)
  private val aisListener = events.aisEvents.tap: messages =>
    ais.onAIS(messages)

  val task = spinnerListener
    .concurrently(coordsListener)
    .concurrently(vesselSearch.task)
    .concurrently(aisListener)
    .concurrently(events.connectivityLogger)

  private def trackLineLayer(id: String, paint: LinePaint, minzoom: Option[Double] = None): Layer =
    Layer.line(id, emptyTrack, paint, minzoom)

  def onCoordsHistory(event: CoordsEvent): Unit =
    val track = event.from
    if !trails.contains(track.track) then
      log.info(s"Installing ${track.trackName} from ${track.times.range}...")
      onCoords(event)
    else ()

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
    trails = trails.updated(
      trackId,
      CoordsEvent(trails.get(trackId).map(_.coords).getOrElse(Nil) ++ coordsInfo, from)
    )
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
      map.putLayer(trackLineLayer(track, LinePaint(LinePaint.blackColor, 1, 1.0d), Option(8d)))
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
                .get(trackId)
                .map(_.coords)
                .getOrElse(Nil)
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
      val totalDistance = calcDistance(froms)
      e.innerHTML = s"${formatDistance(totalDistance)} km"
    elem(ConsumptionId).foreach: e =>
      if from.sourceType == SourceType.Vehicle then
        e.show()
        // Consider computing kWh/100km in the backend
        val carTrails = trails.values.toList
          .filter(t =>
            t.from.sourceType == SourceType.Vehicle && t.from.distanceMeters > 300.meters
          )
        val consumptions: Seq[Energy] = carTrails
          .map: cs =>
            val decrements: Iterator[Energy] = cs.coords
              .flatMap[Energy](_.battery)
              .filter(_.wattHours > 0)
              .sliding(2)
              .collect:
                case Seq(b1, b2) if b2 < b1 => b2.minus(b1)
            val consumption: Double = decrements.map(_.wattHours).sum
            Energy(consumption)
        val consumption = Energy(consumptions.map(_.wattHours).sum.abs)
        val carDistance = calcDistance(carTrails.map(_.from))
        val kwhPer100Km = consumption.wattHours / carDistance.toMeters * 100
        val rounded = "%.2f".format(kwhPer100Km)
        e.innerHTML = s"$rounded kWh/100km"
    elem(TopSpeedId).foreach: e =>
      froms
        .flatMap(_.topSpeed)
        .maxOption
        .foreach: top =>
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
      e.href = BoatHttpRoutes.full(from.trackName)
    anchor(GraphLinkId).foreach: e =>
      e.show()
      e.href = BoatHttpRoutes.chart(from.trackName)
    elem(EditTitleId).foreach: e =>
      e.show()
    elem(DurationId).foreach: e =>
      e.show()
      if froms.nonEmpty then
        val durNanos = froms.map(_.duration.toNanos).sum
        val dur: Duration = Duration.fromNanos(durNanos)
        e.innerHTML = s"${trackLang.duration} ${formatDuration(dur)}"
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
      val eligibleTrails = trails.filter((_, cs) => cs.coords.size > 2)
      val lengths = eligibleTrails.map((_, cs) => cs.coords.size).mkString(", ")
      log.info(s"Fitting to map from ${eligibleTrails.size} trails of lengths $lengths...")
      val trail = eligibleTrails.values.flatMap(_.coords).map(_.coord)
      fitTo(trail.toList)
    else log.info(s"Not fitting, map mode is $mapMode")

  private def calcDistance(ts: Seq[TrackRef]) = DistanceM(
    if ts.isEmpty then 0d else ts.map(_.distanceMeters.toMeters).sum
  )

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
      ConsumptionId,
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
