package com.malliina.boat

import org.scalajs.dom.document
import play.api.libs.json.{Json, Writes}

import scala.scalajs.js.{JSON, URIUtils}

object MapView {

  def apply(accessToken: String): MapView = new MapView(accessToken)

  def apply(): MapView = apply(cookies(Constants.TokenCookieName))

  def cookies = URIUtils.decodeURIComponent(document.cookie).split(";").toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap
}

class MapView(accessToken: String) {
  mapboxGl.accessToken = accessToken

  val mapOptions = MapOptions(
    container = "map",
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(24.9000, 60.1400),
    zoom = 13
  )
  val map = new MapboxMap(mapOptions)
  var socket: Option[MapSocket] = None

  map.on("load", () => {
    socket = Option(new MapSocket(map))
  })

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
