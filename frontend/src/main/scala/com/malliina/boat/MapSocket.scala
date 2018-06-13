package com.malliina.boat

import play.api.libs.json._

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.JSON

class MapSocket(map: MapboxMap, queryString: String) extends BaseSocket(s"/ws/updates?$queryString") {
  val boatIconId = "boat-icon"
  val emptyTrack = lineFor(Nil)

  var boats = Map.empty[String, FeatureCollection]

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
    case SentencesEvent(_, _) => ()
    case PingEvent(_) => ()
    case other => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(coords: Seq[Coord], from: JoinedTrack): Unit = {
    val boat = from.boatName
    val track = trackName(boat)
    val point = pointName(boat)
    val oldTrack = boats.getOrElse(track, emptyTrack)
    val newTrack = oldTrack.addCoords(coords)
    boats = boats.updated(track, newTrack)
    // adds layer if not already added
    if (map.getSource(track).isEmpty) {
      log.debug(s"Crafting new track for boat '$boat'...")
      map.addLayer(parse(animation(track)))
      coords.lastOption.map { coord =>
        map.addLayer(parse(symbolAnimation(point, coord)))
      }
    }
    // updates the data of the layers
    map.getSource(point).foreach { geoJson =>
      coords.lastOption.foreach { coord =>
        // updates placement
        geoJson.setData(parse(pointFor(coord)))
        // updates bearing
        newTrack.features.flatMap(_.geometry.coords).takeRight(2).toList match {
          case prev :: last :: _ =>
            map.setLayoutProperty(point, "icon-rotate", bearing(prev, last))
          case _ =>
            ()
        }
      }
    }
    map.getSource(track).foreach { geoJson =>
      geoJson.setData(parse(newTrack))
    }
    // does not follow if more than one boats are online, since it's not clear what to follow
    if (boats.keySet.size == 1) {
      coords.lastOption.foreach { coord =>
        map.easeTo(EaseOptions(coord))
      }
    }
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

  def lineFor(coords: Seq[Coord]) = FeatureCollection("FeatureCollection", Seq(Feature("Feature", LineGeometry("LineString", coords))))

  def pointFor(coord: Coord) = FeatureCollection("FeatureCollection", Seq(Feature("Feature", PointGeometry("Point", coord))))

  def trackName(boat: BoatName) = s"$boat-track"

  def pointName(boat: BoatName) = s"$boat-point"

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
