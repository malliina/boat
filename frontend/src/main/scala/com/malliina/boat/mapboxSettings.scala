package com.malliina.boat

import org.scalajs.dom.window.localStorage
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.EncoderOps
import io.circe.parser.{decode, parse}

import scala.scalajs.js.Date
import scala.util.Try

case class MapCamera(center: Coord, zoom: Double, timestampMs: Double = Date.now())

object MapCamera:
  implicit val json: Codec[MapCamera] = deriveCodec[MapCamera]

  def default: MapCamera = MapCamera(Coord(lng = Longitude(24.9), lat = Latitude(60.14)), 13)

  def apply(): MapCamera = MapSettings.load().getOrElse(default)

object MapSettings:
  val settingsKey = "map-settings"

  def load(): Either[Object, MapCamera] = for
    str <- Option(localStorage.getItem(settingsKey)).toRight(s"Item not found: '$settingsKey'.")
    json <- parse(str)
    settings <- json.as[MapCamera]
  yield settings

  def save(settings: MapCamera): Unit =
    localStorage.setItem(settingsKey, settings.asJson.noSpaces)
