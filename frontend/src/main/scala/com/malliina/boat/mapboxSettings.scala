package com.malliina.boat

import org.scalajs.dom.window.localStorage
import play.api.libs.json.Json

import scala.scalajs.js.Date
import scala.util.Try

case class MapCamera(center: Coord, zoom: Double, timestampMs: Double = Date.now())

object MapCamera {
  implicit val json = Json.format[MapCamera]

  def default = MapCamera(Coord(lng = Longitude(24.9), lat = Latitude(60.14)), 13)

  def apply() = MapSettings.load().getOrElse(default)
}

object MapSettings {
  val settingsKey = "map-settings"

  def load(): Either[Object, MapCamera] = for {
    str <- Option(localStorage.getItem(settingsKey)).toRight(s"Item not found: '$settingsKey'.")
    json <- Try(Json.parse(str)).toEither
    settings <- json.validate[MapCamera].asEither
  } yield settings

  def save(settings: MapCamera): Unit = {
    localStorage.setItem(settingsKey, Json.stringify(Json.toJson(settings)))
  }
}
