package com.malliina.boat

import cats.effect.Async
import cats.syntax.all.toFunctorOps
import com.malliina.boat.BoatFormats.formatDistance
import com.malliina.boat.PathFinder.*
import com.malliina.http.HttpClient
import com.malliina.mapbox.{MapMouseEvent, MapboxMap, MapboxMarker}
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.{HTMLDivElement, HTMLSpanElement}
import scalatags.JsDom.all.*

import scala.annotation.unused

object PathFinder:
  private val routeLayer = "route"
  private val routeFirstLayer = "route-first"
  private val routeLastLayer = "route-last"
  private val layerIds = Seq(routeLayer, routeFirstLayer, routeLastLayer)

class PathFinder[F[_]: Async](val map: MapboxMap, http: HttpClient[F])
  extends GeoUtils
  with BaseFront:
  private val mapContainer = elemGet[HTMLDivElement](MapId)
  private val routesContainer = elemGet[HTMLDivElement](RoutesContainer)
  var isEnabled: Boolean = false
  var start: Option[MapboxMarker] = None
  var end: Option[MapboxMarker] = None

  def toggleState(): Unit = state(!isEnabled)

  def state(enabled: Boolean): Unit =
    if enabled then
      isEnabled = true
      mapContainer.classList.add(Routes)
      routesContainer.classList.add(Enabled)
      elemAs[HTMLSpanElement](Question).foreach(_.classList.add(Invisible))
    else
      isEnabled = false
      mapContainer.classList.remove(Routes)
      routesContainer.classList.remove(Enabled)
      clear()

  def updatePath(e: MapMouseEvent): Unit =
    if isEnabled then
      val c = Coord.buildOrFail(e.lngLat.lng, e.lngLat.lat)
      def endMarker = MapboxMarker(finishMark(c), c, map)
      (start, end) match
        case (Some(_), Some(oldFinish)) =>
          val newStart = MapboxMarker(startMark(oldFinish.coord), oldFinish.coord, map)
          clear()
          val newEnd = endMarker
          start = Option(newStart)
          end = Option(newEnd)
          findRoute(newStart.coord, newEnd.coord)
        case (Some(startMarker), None) =>
          val newEnd = endMarker
          end = Option(newEnd)
          findRoute(startMarker.coord, newEnd.coord)
        case _ =>
          start = Option(MapboxMarker(startMark(c), c, map))

  private def findRoute(from: Coord, to: Coord) =
    http
      .get[RouteResult](s"/routes/${from.lat}/${from.lng}/${to.lat}/${to.lng}")
      .map: res =>
        val route = res.route
        val coords = route.coords
        val coll = FeatureCollection(
          Seq(Feature(LineGeometry(coords), Map(RouteSpec.Cost -> route.cost.asJson)))
        )
        elemGet[HTMLSpanElement](RouteLength).innerHTML = s"${formatDistance(route.cost)} km"
        drawLine(routeLayer, coll)
        coords.headOption.map: start =>
          val init = lineFor(Seq(from, start))
          drawLine(routeFirstLayer, init, LinePaint.dashed())
        coords.lastOption.foreach: end =>
          val tail = lineFor(Seq(end, to))
          drawLine(routeLastLayer, tail, LinePaint.dashed())

  private def clear(): Unit =
    start.foreach(_.remove())
    start = None
    end.foreach(_.remove())
    end = None
    layerIds.foreach: id =>
      map.removeLayerAndSourceIfExists(id)

  private def startMark(@unused c: Coord) = span(`class` := "marker start")

  private def finishMark(@unused c: Coord) = span(`class` := "marker finish")
