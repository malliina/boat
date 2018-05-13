package com.malliina.boat

import play.api.libs.json.{Json, Writes}

import scala.scalajs.js.JSON

object MapView {
  def apply() = new MapView("pk.eyJ1IjoibWFsbGlpbmEiLCJhIjoiY2pnbnVmbnBwMXpzYTJ3cndqajh2anFmaSJ9.2a0q5s3Tre_4_KFeuCB7iQ")
}

class MapView(accessToken: String) {
  mapboxGl.accessToken = accessToken

  val mapOptions = MapOptions(
    container = "map",
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(24.891515387373488, 60.15350862904472),
    zoom = 13
  )
  val map = new MapboxMap(mapOptions)
  var socket: Option[MapSocket] = None

//  map.on("click", (e: MouseEvent) => {
  //    onCoord(Coord(e.lngLat.lng, e.lngLat.lat))
  //  })

  map.on("load", () => {
    socket = Option(new MapSocket(map))
  })

  def onCoord(coord: Coord): Unit = {
    socket.foreach(_.onCoords(Seq(coord)))
  }

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
