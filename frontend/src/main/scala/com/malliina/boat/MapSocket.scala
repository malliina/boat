package com.malliina.boat

import play.api.libs.json._

import scala.scalajs.js.JSON

class MapSocket(map: MapboxMap) extends BaseSocket("/ws/updates") {
  val animationId = "line-animation"
  var boats = Map.empty[BoatName, FeatureCollection]
  val emptyTrack = FeatureCollection("FeatureCollection", Seq(Feature("Feature", Geometry("LineString", Nil))))

  def animation(id: BoatName) = Animation(id.name, "line", AnimationSource("geojson", emptyTrack), Layout("round", "round"), Paint("#888", 3))

  override def handlePayload(payload: JsValue): Unit =
    payload.validate[FrontEvent].map(consume).recover { case err => onJsonFailure(err) }

  def consume(event: FrontEvent): Unit = event match {
    case CoordsEvent(coords, boat) => onCoords(coords, boat)
    case SentencesEvent(_) => ()
    case other => log.info(s"Unknown event: '$other'.")
  }

  def onCoords(coords: Seq[Coord], from: BoatName): Unit = {
    val oldTrack = boats.getOrElse(from, emptyTrack)
    val newTrack = oldTrack.addCoords(coords)
    boats = boats.updated(from, newTrack)
    if (map.getSource(from.name).isEmpty) {
      log.debug("Crafting new track...")
      val lineLayer = parse(animation(from))
      map.addLayer(lineLayer)
    }
    map.getSource(from.name).foreach { geoJson =>
      geoJson.setData(parse(newTrack))
    }
    // Does not follow if more than one boats are online, since it's not clear what to follow
    if (boats.keySet.size == 1) {
      coords.lastOption.foreach { coord =>
        map.easeTo(EaseOptions(coord))
      }
    }
  }

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
