package com.malliina.boat

import com.malliina.boat.BoatFormats._
import com.malliina.geojson.{GeoLineString, GeoPoint}
import com.malliina.mapbox._
import com.malliina.turf.nearestPointOnLine
import com.malliina.values.ErrorMessage
import play.api.libs.json._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class MapSocket(val map: MapboxMap,
                pathFinder: PathFinder,
                track: PathState,
                sample: Option[Int],
                mode: MapMode,
                language: Language)
    extends BoatSocket(track, sample)
    with GeoUtils {

  val lang = Lang(language)
  val trackLang = lang.track
  val emptyTrack = lineFor(Nil)
  val trackPopup = MapboxPopup(PopupOptions())
  val boatPopup = MapboxPopup(PopupOptions(className = Option("popup-boat")))
  val ais = AISRenderer(map)
  val html = Popups(lang)
  val popups = MapMouseListener(map, pathFinder, ais, html)

  private var mapMode: MapMode = mode
  private var boats = Map.empty[String, FeatureCollection]
  private var trails = Map.empty[TrackId, Seq[TimedCoord]]
  private var devices = Map.empty[BoatName, DeviceProps]

  val imageInits = Future.sequence(
    Seq(
      initImage("boat-resized-opt-20.png", boatIconId),
      initImage("trophy-color-12.png", trophyIconId),
      initImage("satellite-dish-20.png", deviceIconId)
    )
  )

  def initImage(file: String, iconId: String): Future[Unit] =
    map.initImage(s"/assets/img/$file", iconId).recover {
      case t => log.error("Unable to initialize image.", t)
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
    val trophyLayerId = trophyName(from.trackName)
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
      coords.lastOption.foreach { coord =>
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
      // adds trophy icon
      map.putLayer(trophySymbolLayer(trophyLayerId, from.topPoint.coord))
      map.onHover(trophyLayerId)(
        in => {
          map.getCanvas().style.cursor = "pointer"
        },
        _ => {
          map.getCanvas().style.cursor = ""
        }
      )

      map.onHover(hoverableTrack)(
        in => {
          val isOnBoatSymbol = map
            .queryRendered(in.point, QueryOptions.all)
            .getOrElse(Nil)
            .exists(_.layer.exists(_.id == point))
          if (!isOnBoatSymbol) {
            val lngLat = in.lngLat
            val op = Coord.build(lngLat.lng, lngLat.lat).flatMap { coord =>
              nearest(coord, trails.getOrElse(trackId, Nil))(_.coord).map { near =>
                map.getCanvas().style.cursor = "pointer"
                popups.isTrackHover = true
                trackPopup.show(html.track(PointProps(near, from)), in.lngLat, map)
              }
            }
            op.fold(err => log.info(err.message), identity)
          }
        },
        _ => {
          map.getCanvas().style.cursor = ""
          popups.isTrackHover = false
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
            map.setLayoutProperty(point, ImageLayout.IconRotate, bearing(prev, last).toInt)
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
    // updates the trophy icon
    val topPoint = from.topPoint
    map.findSource(trophyLayerId).foreach { geoJson =>
      geoJson.updateData(pointForProps(topPoint.coord, PointProps(topPoint, from)))
    }
    val trail: Seq[Coord] = newTrack.features.flatMap(_.geometry.coords)
    elem(TitleId).foreach { e =>
      from.trackTitle.foreach { title =>
        e.classList.add("show")
        e.innerHTML = title.title
      }
    }
    elem(DistanceId).foreach { e =>
      e.innerHTML = s"${formatDistance(from.distanceMeters)} km"
    }
    elem(TopSpeedId).foreach { e =>
      from.topSpeed.foreach { top =>
        e.innerHTML = s"${trackLang.top} ${formatSpeed(top)} kn"
      }
    }
    elem(WaterTempId).foreach { e =>
      coordsInfo.lastOption.map(_.waterTemp).foreach { temp =>
        e.innerHTML = s"${trackLang.water} ${formatTemp(temp)} ℃"
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
        e.innerHTML = s"${trackLang.duration} ${formatDuration(from.duration)}"
      }
    }
    // updates the map position, zoom to reflect the updated track(s)
    mapMode match {
      case MapMode.Fit =>
        trail.headOption.foreach { head =>
          val init = LngLatBounds(head)
          val bs: LngLatBounds = trail.drop(1).foldLeft(init) { (bounds, c) =>
            bounds.extend(LngLat(c))
          }
          try {
            map.fitBounds(bs, SimplePaddingOptions(20))
          } catch {
            case e: Exception =>
              log.error(s"Unable to fit using ${bs.getSouthWest()} ${bs.getNorthWest()} ${bs
                .getNorthEast()} ${bs.getSouthEast()}", e)
          }
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

  val devicePopup = MapboxPopup(PopupOptions(className = Option("popup-device")))

  override def onGps(event: GPSCoordsEvent): Unit = {
    val from = event.from
    val name = deviceName(from.deviceName)
    event.coords.lastOption.foreach { coord =>
      val props = DeviceProps(coord, from)
      devices = devices.updated(from.deviceName, props)
      map
        .findSource(name)
        .map { geoJson =>
          geoJson.updateData(pointForProps(coord.coord, props))
        }
        .getOrElse {
          map.putLayer(deviceSymbolLayer(name, coord.coord, props))
          map.onHover(name)(
            in =>
              map.queryRendered(in.point, QueryOptions.layer(name)).map { fs =>
                fs.flatMap(_.props.asOpt[DeviceProps]).headOption.foreach { device =>
                  map.getCanvas().style.cursor = "pointer"
                  if (!popups.markPopup.isOpen())
                    devicePopup.showText(device.deviceName.name, in.lngLat, map)
                }
            },
            _ => {
              map.getCanvas().style.cursor = ""
              devicePopup.remove()
            }
          )
        }
    }
  }

  def fly(to: BoatName): Unit =
    devices.get(to).map { device =>
      map.flyTo(FlyOptions(device.coord, 0.8))
    }.getOrElse {
      log.info(s"Device not found on map: '$to'.")
    }

  override def onAIS(messages: Seq[VesselInfo]): Unit = {
    ais.onAIS(messages)
  }

  def nearest[T](fromCoord: Coord, on: Seq[T])(c: T => Coord): Either[ErrorMessage, T] = {
    val all = GeoLineString(on.map(c))
    val turfPoint = GeoPoint(fromCoord)
    val nearestResult = nearestPointOnLine(all, turfPoint)
    val idx = nearestResult.properties.index
    if (on.length > idx) Right(on(idx))
    else Left(ErrorMessage(s"No trail at $fromCoord."))
  }

  // https://www.movable-type.co.uk/scripts/latlong.html
  def bearing(from: Coord, to: Coord): Double = {
    val dLon = to.lng.lng - from.lng.lng
    val y = Math.sin(dLon) * Math.cos(to.lat.lat)
    val x = Math.cos(from.lat.lat) * Math.sin(to.lat.lat) - Math.sin(from.lat.lat) * Math.cos(
      to.lat.lat) * Math.cos(dLon)
    val brng = toDeg(Math.atan2(y, x))
    360 - ((brng + 360) % 360)
  }

  def toRad(deg: Double) = deg * Math.PI / 180
  def toDeg(rad: Double) = rad * 180 / Math.PI

  def trackName(boat: BoatName) = s"track-$boat"
  def pointName(boat: BoatName) = s"boat-$boat"
  def deviceName(device: BoatName) = s"$DevicePrefix-$device"
  def trophyName(track: TrackName) = s"$TrophyPrefix-$track"

  def boatSymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(boatIconId, `icon-size` = 1))

  def deviceSymbolLayer(id: String, coord: Coord, props: DeviceProps) =
    Layer.symbol(id, pointForProps(coord, props), ImageLayout(deviceIconId, `icon-size` = 1))

  def trophySymbolLayer(id: String, coord: Coord) =
    Layer.symbol(id, pointFor(coord), ImageLayout(trophyIconId, `icon-size` = 1))
}

trait GeoUtils {
  val boatIconId = "boat-icon"
  val trophyIconId = "trophy-icon"
  val deviceIconId = "device-icon"

  def map: MapboxMap

  def updateOrSet(layer: Layer): Outcome =
    map
      .findSource(layer.id)
      .map { geo =>
        layer.source match {
          case InlineLayerSource(_, data) =>
            geo.updateData(data)
            Outcome.Updated
          case StringLayerSource(_) =>
            Outcome.Noop
        }
      }
      .getOrElse {
        map.putLayer(layer)
        Outcome.Added
      }

  def drawLine(id: String, geoJson: FeatureCollection, paint: LinePaint = LinePaint.thin()) =
    updateOrSet(Layer.line(id, geoJson, paint, None))

  def lineFor(coords: Seq[Coord]) = collectionFor(LineGeometry(coords), Map.empty)

  def pointFor(coord: Coord, props: Map[String, JsValue] = Map.empty) =
    collectionFor(PointGeometry(coord), props)

  def pointForProps[T: OWrites](coord: Coord, props: T) =
    pointFor(coord, Json.toJsObject(props).value.toMap)

  def collectionFor(geo: Geometry, props: Map[String, JsValue]): FeatureCollection =
    FeatureCollection(Seq(Feature(geo, props)))
}
