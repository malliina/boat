package com.malliina.boat

import com.malliina.boat.BoatFormats._
import com.malliina.boat.Parsing._
import com.malliina.mapbox._
import com.malliina.turf.turf
import com.malliina.util.EitherOps
import com.malliina.values.ErrorMessage
import play.api.libs.json._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters.genTravConvertible2JSRichGenTrav

class MapSocket(val map: MapboxMap,
                track: Option[TrackName],
                sample: Option[Int],
                mode: MapMode,
                language: Language)
  extends BoatSocket(track, sample) with GeoUtils {

  val lang = Lang(language)
  val emptyTrack = lineFor(Nil)
  val trackPopup = MapboxPopup(PopupOptions())
  val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))
  val markPopup = MapboxPopup(PopupOptions())
  val html = Popups(lang)
  val ais = AISRenderer(map)

  private var mapMode: MapMode = mode
  private var boats = Map.empty[String, FeatureCollection]
  private var trails = Map.empty[TrackId, Seq[TimedCoord]]
  private var topSpeedMarkers = Map.empty[TrackId, ActiveMarker]
  private var isTrackHover: Boolean = false

  initImage()

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

  def initImage(): Future[Unit] =
    map.initImage("/assets/img/boat-resized-opt-20.png", boatIconId).recover {
      case t => log.error(t)
    }

  def lineLayer(id: String) = trackLineLayer(id, LinePaint.thin())

  def trackLineLayer(id: String, paint: LinePaint) = Layer.line(id, emptyTrack, paint, None)

  override def onCoords(event: CoordsEvent): Unit = {
    val from = event.from
    val trackId = from.track
    val coordsInfo = event.coords
    val coords = coordsInfo.map(_.coord)
    val boat = from.boatName
    val track = trackName(boat)
    val hoverableTrack = s"$track-thick"
    val point = pointName(boat)
    val oldTrack: FeatureCollection = boats.getOrElse(track, emptyTrack)
    val newTrack: FeatureCollection = oldTrack.addCoords(coords)
    boats = boats.updated(track, newTrack)
    trails = trails.updated(trackId, trails.getOrElse(trackId, Nil) ++ coordsInfo)
    // adds layer if not already added
    if (map.findSource(track).isEmpty) {
      log.debug(s"Crafting new track for boat '$boat'...")
      map.putLayer(lineLayer(track))
      // adds a thicker, transparent trail on top of the visible one, which represents the mouse-hoverable area
      map.putLayer(trackLineLayer(hoverableTrack, LinePaint(LinePaint.blackColor, 5, 0)))
      coords.lastOption.map { coord =>
        // adds boat icon
        map.putLayer(boatSymbolLayer(point, coord))
        map.onHover(point)(
          in => {
            map.getCanvas().style.cursor = "pointer"
            trackPopup.remove()
            boatPopup.showText(from.boatName.name, in.lngLat, map)
          },
          _ => {
            map.getCanvas().style.cursor = ""
            boatPopup.remove()
          }
        )
      }
      map.onHover(hoverableTrack)(
        in => {
          val isOnBoatSymbol = map.queryRendered(in.point, QueryOptions.all).getOrElse(Nil)
            .exists(_.layer.exists(_.id == point))
          if (!isOnBoatSymbol) {
            val op = nearest(in.lngLat, trails.getOrElse(trackId, Nil))(_.coord).map { near =>
              map.getCanvas().style.cursor = "pointer"
              isTrackHover = true
              trackPopup.show(html.track(near, from), in.lngLat, map)
            }
            op.fold(err => log.info(err), identity)
          }
        },
        _ => {
          map.getCanvas().style.cursor = ""
          isTrackHover = false
          trackPopup.remove()
        }
      )
    }
    // updates the boat icon
    map.findSource(point).foreach { geoJson =>
      coords.lastOption.foreach { coord =>
        // updates placement
        geoJson.updateData(pointFor(coord))
        // updates bearing
        newTrack.features.flatMap(_.geometry.coords).takeRight(2).toList match {
          case prev :: last :: _ =>
            map.setLayoutProperty(point, ImageLayout.IconRotate, bearing(prev, last))
          case _ =>
            ()
        }
      }
    }
    // updates the trail
    map.findSource(track).foreach { geoJson =>
      geoJson.updateData(newTrack)
    }
    map.findSource(hoverableTrack).foreach { geoJson =>
      geoJson.updateData(newTrack)
    }
    val topPoint = from.topPoint
    val isSameTopSpeed = topSpeedMarkers.get(trackId).exists(m => m.at.id == from.topPoint.id)
    if (!isSameTopSpeed) {
      topSpeedMarkers.get(trackId).foreach(_.marker.remove())
      // https://www.mapbox.com/mapbox-gl-js/example/set-popup/
      val markerPopup = MapboxPopup(PopupOptions(offset = Option(6)))
        .html(html.track(topPoint, from))
      val marker = MapboxMarker(html.marker(topPoint.speed), topPoint.coord, markerPopup, map)
      val newTopSpeed = ActiveMarker(marker, topPoint)
      topSpeedMarkers = topSpeedMarkers.updated(trackId, newTopSpeed)
    }
    val trail: Seq[Coord] = newTrack.features.flatMap(_.geometry.coords)
    elem(TitleId).foreach { e =>
      from.trackTitle.foreach { title =>
        e.classList.add("show")
        e.innerHTML = title.title
      }
    }
    elem(DistanceId).foreach { e =>
      e.innerHTML = s"${formatDistance(from.distance)} km"
    }
    elem(TopSpeedId).foreach { e =>
      from.topSpeed.foreach { top =>
        e.innerHTML = s"${lang.top} ${formatSpeed(top)} kn"
      }
    }
    elem(WaterTempId).foreach { e =>
      coordsInfo.lastOption.map(_.waterTemp).foreach { temp =>
        e.innerHTML = s"${lang.water} ${formatTemp(temp)} ℃"
      }
    }
    anchor(FullLinkId).foreach { e =>
      e.show()
      e.href = s"/tracks/${from.trackName}/full"
    }
    anchor(GraphLinkId).foreach { e =>
      e.show()
      e.href = s"/tracks/${from.trackName}/chart"
    }
    elem(EditTitleId).foreach { e =>
      e.show()
    }
    if (boats.keySet.size == 1) {
      elem(DurationId).foreach { e =>
        e.innerHTML = s"${lang.duration} ${formatDuration(from.duration)}"
      }
    }
    // updates the map position, zoom to reflect the updated track(s)
    mapMode match {
      case MapMode.Fit =>
        trail.headOption.foreach { coord =>
          val init = LngLatBounds(coord)
          val bs: LngLatBounds = trail.foldLeft(init) { (bounds, c) =>
            bounds.extendWith(c)
          }
          map.fitBounds(bs, FitOptions(20))
        }
        mapMode = MapMode.Follow
      case MapMode.Follow =>
        if (boats.keySet.size == 1) {
          coords.lastOption.foreach { coord =>
            map.easeTo(EaseOptions(coord))
          }
        } else {
          // does not follow if more than one boats are online, since it's not clear what to follow
          mapMode = MapMode.Stay
        }
      case MapMode.Stay =>
        ()
    }

  }

  override def onAIS(messages: Seq[VesselInfo]): Unit = {
    ais.onAIS(messages)
  }

  def nearest[T](from: LngLat, on: Seq[T])(c: T => Coord): Either[String, T] = {
    // TODO try to fix the toJSArray nonsense
    val all = turf.lineString(on.map(t => c(t).toArray.toJSArray).toArray.toJSArray)
    val fromCoord = Coord(from.lng, from.lat)
    val nearestResult = turf.nearestPointOnLine(all, turf.point(fromCoord.toArray.toJSArray))
    for {
      feature <- asJson[Feature](nearestResult).left.map(err => s"Feature JSON failed: '$err'.")
      idxJson <- feature.properties.get("index").toRight("No index in feature properties.")
      idx <- validate[Int](idxJson).left.map(err => s"Index JSON failed: '$err'.")
      nearest <- if (on.length > idx) Right(on(idx)) else Left(s"No trail at $from.")
    } yield nearest
  }

  // https://www.movable-type.co.uk/scripts/latlong.html
  def bearing(from: Coord, to: Coord): Double = {
    val dLon = to.lng - from.lng
    val y = Math.sin(dLon) * Math.cos(to.lat)
    val x = Math.cos(from.lat) * Math.sin(to.lat) - Math.sin(from.lat) * Math.cos(to.lat) * Math.cos(dLon)
    val brng = toDeg(Math.atan2(y, x))
    360 - ((brng + 360) % 360)
  }

  def toRad(deg: Double) = deg * Math.PI / 180

  def toDeg(rad: Double) = rad * 180 / Math.PI

  def trackName(boat: BoatName) = s"track-$boat"

  def pointName(boat: BoatName) = s"boat-$boat"

  def boatSymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(boatIconId, `icon-size` = 1))
}

trait GeoUtils {
  val boatIconId = "boat-icon"

  def map: MapboxMap

  def updateOrSet(layer: Layer): Outcome =
    map.findSource(layer.id).map { geo =>
      layer.source match {
        case InlineLayerSource(_, data) =>
          geo.updateData(data)
          Outcome.Updated
        case StringLayerSource(_) =>
          Outcome.Noop
      }
    }.getOrElse {
      map.putLayer(layer)
      Outcome.Added
    }

  def lineFor(coords: Seq[Coord]) = collectionFor(LineGeometry(coords), Map.empty)

  def pointFor(coord: Coord) = collectionFor(PointGeometry(coord), Map.empty)

  def collectionFor(geo: Geometry, props: Map[String, JsValue]): FeatureCollection =
    FeatureCollection(Seq(Feature(geo, props)))
}