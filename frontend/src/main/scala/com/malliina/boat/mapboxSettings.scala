package com.malliina.boat

import org.scalajs.dom.document
import org.scalajs.dom.window.localStorage
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.EncoderOps
import io.circe.parser.{decode, parse}

import scala.scalajs.js.Date
import scala.util.Try

case class MapCamera(
  center: Coord,
  zoom: Double,
  customCenter: Boolean,
  timestampMs: Double = Date.now()
)

object MapCamera:
  implicit val json: Codec[MapCamera] = deriveCodec[MapCamera]

  private val center =
    for
      center <- Option(document.getElementById(FrontKeys.Center))
      lng <- Option(center.getAttribute(s"data-${FrontKeys.Lng}")).flatMap(_.toDoubleOption)
      lat <- Option(center.getAttribute(s"data-${FrontKeys.Lat}")).flatMap(_.toDoubleOption)
    yield Coord(Longitude(lng), Latitude(lat))
  private val defaultCenter = Coord(lng = Longitude(24.9), lat = Latitude(60.14))

  private def default: MapCamera = MapCamera(center.getOrElse(defaultCenter), 13, false)
  def apply(): MapCamera = MapSettings.load(center).getOrElse(default)

object MapSettings:
  private val settingsKey = "map-settings"

  def load(center: Option[Coord]): Either[Object, MapCamera] = for
    str <- Option(localStorage.getItem(settingsKey)).toRight(s"Item not found: '$settingsKey'.")
    json <- parse(str)
    settings <- json.as[MapCamera]
  yield settings.copy(
    center = center.getOrElse(settings.center),
    zoom = center.map(_ => 12d).getOrElse(settings.zoom),
    customCenter = center.isDefined
  )

  def save(settings: MapCamera): Unit =
    localStorage.setItem(settingsKey, settings.copy(customCenter = false).asJson.noSpaces)
