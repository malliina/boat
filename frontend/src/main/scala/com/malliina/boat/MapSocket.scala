package com.malliina.boat

import com.malliina.boat.FrontKeys.{DistanceId, DurationId, TopSpeedId, WaterTempId}
import com.malliina.measure.Speed
import org.scalajs.dom.document
import play.api.libs.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.genTravConvertible2JSRichGenTrav
import scala.scalajs.js.JSON

class MapSocket(map: MapboxMap, queryString: String) extends BaseSocket(s"/ws/updates?$queryString") {
  val boatIconId = "boat-icon"
  val emptyTrack = lineFor(Nil)

  private var mapMode: MapMode = Fit
  private var boats = Map.empty[String, FeatureCollection]
  private var latestMaxSpeed: Option[Speed] = None

  initImage()

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

  def animation(id: String) = Animation(
    id,
    LineLayer,
    AnimationSource("geojson", emptyTrack),
    LineLayout("round", "round"),
    Option(Paint("#000", 1))
  )

  override def handlePayload(payload: JsValue): Unit =
    payload.validate[FrontEvent].map(consume).recover { case err => onJsonFailure(err) }

  def consume(event: FrontEvent): Unit = event match {
    case CoordsEvent(coords, track) if coords.nonEmpty => onCoords(coords, track)
    case CoordsBatch(coords) if coords.nonEmpty => coords.foreach(e => onCoords(e.coords, e.from))
    case SentencesEvent(_, _) => ()
    case PingEvent(_) => ()
    case other => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(coordsInfo: Seq[TimedCoord], from: TrackMetaLike): Unit = {
    val coords = coordsInfo.map(_.coord)
    val boat = from.boatName
    val track = trackName(boat)
    val point = pointName(boat)
    val oldTrack = boats.getOrElse(track, emptyTrack)
    val newTrack: FeatureCollection = oldTrack.addCoords(coords)
    boats = boats.updated(track, newTrack)
    // adds layer if not already added
    if (map.getSource(track).isEmpty) {
      log.debug(s"Crafting new track for boat '$boat'...")
      map.addLayer(toJson(animation(track)))
      coords.lastOption.map { coord =>
        map.addLayer(toJson(symbolAnimation(point, coord)))
      }
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
    val trail: Seq[Coord] = newTrack.features.flatMap(_.geometry.coords)
    elem(DistanceId).foreach { e =>
      val totalLength = boats.values.flatMap(fc => fc.features.headOption.map(f => turf.length(toJson(f)))).sum
      e.innerHTML = s"${formatDouble(totalLength)} km"
    }
    elem(TopSpeedId).foreach { e =>
      val maxSpeed = if (coordsInfo.isEmpty) Speed.zero else coordsInfo.map(_.speed).max
      if (maxSpeed > latestMaxSpeed.getOrElse(Speed.zero)) {
        e.innerHTML = s"Top ${formatDouble(maxSpeed.toKnots)} kn"
        latestMaxSpeed = Option(maxSpeed)
      }
    }
    elem(WaterTempId).foreach { e =>
      coordsInfo.lastOption.map(_.waterTemp).foreach { temp =>
        e.innerHTML = s"Water ${formatTemp(temp.toCelsius)} ℃"
      }
    }
    if (boats.keySet.size == 1) {
      elem(DurationId).foreach { e =>
//        e.innerHTML = s"Time ${formatDuration(from.duration)}"
      }
    }
    // updates the map position, zoom to reflect the updated track(s)
    mapMode match {
      case Fit =>
        trail.headOption.foreach { coord =>
          val init = new LngLatBounds(coord.toArray.toJSArray, coord.toArray.toJSArray)
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

  def formatDuration(d: Duration): String = {
    val seconds = d.toSeconds
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = (seconds / (60 * 60)) % 24
    if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
  }

  def formatDouble(d: Double) = "%.3f".format(d)

  def formatTemp(d: Double) = "%.1f".format(d)

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

  def lineFor(coords: Seq[Coord]) = collectionFor(LineGeometry("LineString", coords))

  def pointFor(coord: Coord) = collectionFor(PointGeometry("Point", coord))

  def collectionFor(geo: Geometry) = FeatureCollection("FeatureCollection", Seq(Feature("Feature", geo)))

  def trackName(boat: BoatName) = s"$boat-track"

  def pointName(boat: BoatName) = s"$boat-point"

  def elem(id: String) = Option(document.getElementById(id))

  def toJson[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))

  def asJson[T: Reads](in: js.Any): Either[JsError, T] =
    Json.parse(JSON.stringify(in)).validate[T].asEither.left.map(err => JsError(err))
}
