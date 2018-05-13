package com.malliina.boat

import play.api.libs.json._

import scala.scalajs.js.JSON

class MapSocket(map: MapboxMap) extends BaseSocket("/ws/updates") {
  val animationId = "line-animation"
  var geo = FeatureCollection("FeatureCollection", Seq(Feature("Feature", Geometry("LineString", Nil))))
  val anim = Animation(animationId, "line", AnimationSource("geojson", geo), Layout("round", "round"), Paint("#888", 8))

  map.on("load", () => {
    val lineLayer = parse(anim)
    map.addLayer(lineLayer)
  })

  val lineLayer = parse(anim)
  map.addLayer(lineLayer)

  override def handlePayload(payload: JsValue): Unit =
    payload.validate[FrontEvent].map(consume).recover { case err => onJsonFailure(err) }

  def consume(event: FrontEvent): Unit = {
    event match {
      case CoordsEvent(coords) => onCoords(coords)
      case SentencesEvent(_) => ()
      case other => log.info(s"Unknown event: '$other'.")
    }
  }

  def onCoords(coords: Seq[Coord]): Unit = {
    geo = geo.addCoords(coords)
    map.getSource(animationId).setData(parse(geo))
    coords.lastOption.foreach { coord =>
      map.flyTo(FlyOptions(coord, 0.1))
    }
  }

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
