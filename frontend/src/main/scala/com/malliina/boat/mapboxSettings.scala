package com.malliina.boat

import org.scalajs.dom.document
import org.scalajs.dom.window.localStorage
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.EncoderOps
import io.circe.parser.parse

import scala.scalajs.js.Date

case class MapCamera(
  center: Coord,
  zoom: Double,
  customCenter: Boolean,
  timestampMs: Double = Date.now()
) derives Codec.AsObject

object MapCamera:
  private val center =
    for
      center <- Option(document.getElementById(FrontKeys.Center))
      lng <- Option(center.getAttribute(s"data-${FrontKeys.Lng}")).flatMap(_.toDoubleOption)
      longitude <- Longitude.build(lng).toOption
      lat <- Option(center.getAttribute(s"data-${FrontKeys.Lat}")).flatMap(_.toDoubleOption)
      latitude <- Latitude.build(lat).toOption
    yield Coord(longitude, latitude)
  private val defaultCenter =
    Coord(lng = Longitude.build(24.9).toOption.get, lat = Latitude.build(60.14).toOption.get)

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
