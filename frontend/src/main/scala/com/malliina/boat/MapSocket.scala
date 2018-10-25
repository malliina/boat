package com.malliina.boat

import com.malliina.boat.BoatFormats._
import com.malliina.boat.Parsing._
import com.malliina.mapbox._
import com.malliina.turf.turf
import play.api.libs.json._

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.JSConverters.genTravConvertible2JSRichGenTrav

class MapSocket(map: MapboxMap, queryString: String, mode: MapMode)
  extends BoatSocket(s"/ws/updates?$queryString") {

  val boatIconId = "boat-icon"
  val emptyTrack = lineFor(Nil)
  val trackPopup = MapboxPopup(PopupOptions())
  val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))
  val markPopup = MapboxPopup(PopupOptions())

  private var mapMode: MapMode = mode
  private var boats = Map.empty[String, FeatureCollection]
  private var trails = Map.empty[TrackId, Seq[TimedCoord]]
  private var topSpeedMarkers = Map.empty[TrackId, ActiveMarker]

  initImage()

  map.on("click", e => {
    //    log.info(JSON.stringify(map.queryRenderedFeatures(e.point)))
    val features = map.queryRendered(e.point).fold(
      err => {
        log.info(s"Failed to parse features '${err.error}' in '${err.json}'.")
        Nil
      },
      identity
    )
    val symbol = features.find { f =>
      f.geometry.typeName == "Point" &&
        f.layer.exists(obj => (obj \ "type").asOpt[String].exists(t => t == "symbol" || t == "circle"))
    }
    symbol.fold(markPopup.remove()) { feature =>
      val symbol = validate[MarineSymbol](JsObject(feature.properties))
      symbol.fold(
        err => {
          log.info(err.describe)
        },
        ok => {
          val target = feature.geometry.coords.headOption.map(LngLat.apply).getOrElse(e.lngLat)
          markPopup.show(BoatHtml.markPopup(ok), target, map)
        }
      )
    }
  })
  MapboxStyles.clickableLayers.foreach { id =>
    map.onHover(id)(
      in = _ => map.getCanvas().style.cursor = "pointer",
      out = _ => map.getCanvas().style.cursor = ""
    )
  }

  def initImage(): Future[Unit] = {
    val p = Promise[Unit]()
    map.loadImage("/assets/img/boat-resized-opt-20.png", (err, data) => {
      if (err == null) {
        map.addImage(boatIconId, data)
        p.success(())
      } else {
        p.failure(new Exception("Failed to load icon."))
      }
    })
    p.future
  }

  def symbolAnimation(id: String, coord: Coord) = Animation(
    id,
    SymbolLayer,
    AnimationSource("geojson", pointFor(coord)),
    ImageLayout(boatIconId, `icon-size` = 1),
    None
  )

  def animation(id: String) = paintedAnimation(id, Paint("#000", 1, 1))

  def paintedAnimation(id: String, paint: Paint) = Animation(
    id,
    LineLayer,
    AnimationSource("geojson", emptyTrack),
    LineLayout("round", "round"),
    Option(paint)
  )

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
    if (map.getSource(track).isEmpty) {
      log.debug(s"Crafting new track for boat '$boat'...")
      map.putLayer(animation(track))
      // Adds a thicker, transparent trail on top of the visible one, which represents the mouse-hoverable area
      map.putLayer(paintedAnimation(hoverableTrack, Paint("#000", 5, 0)))
      coords.lastOption.map { coord =>
        map.putLayer(symbolAnimation(point, coord))
        map.on("mousemove", point, e => {
          map.getCanvas().style.cursor = "pointer"
          trackPopup.remove()
          boatPopup.showText(from.boatName.name, e.lngLat, map)
        })
        map.on("mouseleave", point, () => {
          map.getCanvas().style.cursor = ""
          boatPopup.remove()
        })
      }
      map.on("mousemove", hoverableTrack, e => {
        val isOnBoatSymbol = asJson[Seq[JsObject]](map.queryRenderedFeatures(e.point))
          .getOrElse(Nil)
          .exists(obj => (obj \ "layer" \ "id").asOpt[String].contains(point))
        if (!isOnBoatSymbol) {
          val op = nearest(e.lngLat, trails.getOrElse(trackId, Nil))(_.coord).map { near =>
            map.getCanvas().style.cursor = "pointer"
            trackPopup.show(BoatHtml.trackPopup(near, from), e.lngLat, map)
          }
          op.fold(err => log.info(err), identity)
        }
      })
      map.on("mouseleave", hoverableTrack, () => {
        map.getCanvas().style.cursor = ""
        trackPopup.remove()
      })
    }
    // updates the boat icon
    map.getSource(point).foreach { geoJson =>
      coords.lastOption.foreach { coord =>
        // updates placement
        geoJson.setData(toJson(pointFor(coord)))
        // updates bearing
        newTrack.features.flatMap(_.geometry.coords).takeRight(2).toList match {
          case prev :: last :: _ =>
            map.setLayoutProperty(point, "icon-rotate", bearing(prev, last))
          case _ =>
            ()
        }
      }
    }
    // updates the trail
    map.getSource(track).foreach { geoJson =>
      geoJson.setData(toJson(newTrack))
    }
    map.getSource(hoverableTrack).foreach { geoJson =>
      geoJson.setData(toJson(newTrack))
    }
    val topPoint = from.topPoint
    val isSameTopSpeed = topSpeedMarkers.get(trackId).exists(m => m.at.id == from.topPoint.id)
    if (!isSameTopSpeed) {
      topSpeedMarkers.get(trackId).foreach(_.marker.remove())
      // https://www.mapbox.com/mapbox-gl-js/example/set-popup/
      val markerPopup = new MapboxPopup(PopupOptions(offset = Option(6)))
        .setHTML(BoatHtml.trackPopup(topPoint, from).render.outerHTML)
      val marker = MapboxMarker(BoatHtml.marker(topPoint.speed), topPoint.coord, markerPopup, map)
      val newTopSpeed = ActiveMarker(marker, topPoint)
      topSpeedMarkers = topSpeedMarkers.updated(trackId, newTopSpeed)
    }
    val trail: Seq[Coord] = newTrack.features.flatMap(_.geometry.coords)
    elem(DistanceId).foreach { e =>
      e.innerHTML = s"${formatDistance(from.distance)} km"
    }
    elem(TopSpeedId).foreach { e =>
      from.topSpeed.foreach { top =>
        e.innerHTML = s"Top ${formatSpeed(top)} kn"
      }
    }
    elem(WaterTempId).foreach { e =>
      coordsInfo.lastOption.map(_.waterTemp).foreach { temp =>
        e.innerHTML = s"Water ${formatTemp(temp)} ℃"
      }
    }
    elem(FullLinkId).foreach { e =>
      e.classList.remove(Hidden)
      e.setAttribute("href", s"/tracks/${from.trackName}/full")
    }
    elem(GraphLinkId).foreach { e =>
      e.classList.remove(Hidden)
      e.setAttribute("href", s"/tracks/${from.trackName}/chart")
    }
    if (boats.keySet.size == 1) {
      elem(DurationId).foreach { e =>
        e.innerHTML = s"Time ${formatDuration(from.duration)}"
      }
    }
    // updates the map position, zoom to reflect the updated track(s)
    mapMode match {
      case Fit =>
        trail.headOption.foreach { coord =>
          val init = LngLatBounds(coord)
          val bs: LngLatBounds = trail.foldLeft(init) { (bounds, c) =>
            bounds.extend(c.toArray.toJSArray)
          }
          map.fitBounds(bs, FitOptions(20))
        }
        mapMode = Follow
      case Follow =>
        if (boats.keySet.size == 1) {
          coords.lastOption.foreach { coord =>
            map.easeTo(EaseOptions(coord))
          }
        } else {
          // does not follow if more than one boats are online, since it's not clear what to follow
          mapMode = Stay
        }
      case Stay =>
        ()
    }

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

  def lineFor(coords: Seq[Coord]) = collectionFor(LineGeometry(coords), Map.empty)

  def pointFor(coord: Coord) = collectionFor(PointGeometry(coord), Map.empty)

  def collectionFor(geo: Geometry, props: Map[String, JsValue]): FeatureCollection =
    FeatureCollection(Seq(Feature(geo, props)))

  def trackName(boat: BoatName) = s"track-$boat"

  def pointName(boat: BoatName) = s"boat-$boat"
}
