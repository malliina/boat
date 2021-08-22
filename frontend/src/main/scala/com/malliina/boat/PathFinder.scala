package com.malliina.boat

import com.malliina.boat.BoatFormats.formatDistance
import com.malliina.boat.PathFinder._
import com.malliina.http.HttpClient
import com.malliina.mapbox.{MapMouseEvent, MapboxMap, MapboxMarker}
import org.scalajs.dom.raw.{HTMLDivElement, HTMLSpanElement}
import scalatags.JsDom.all._
import io.circe._
import io.circe.syntax.EncoderOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PathFinder {
  val routeLayer = "route"
  val routeFirstLayer = "route-first"
  val routeLastLayer = "route-last"
  val layerIds = Seq(routeLayer, routeFirstLayer, routeLastLayer)

  def apply(map: MapboxMap): PathFinder = new PathFinder(map)
}

class PathFinder(val map: MapboxMap) extends GeoUtils with BaseFront {
  val mapContainer = elemGet[HTMLDivElement](MapId)
  val routesContainer = elemGet[HTMLDivElement](RoutesContainer)
  var isEnabled: Boolean = false
  var start: Option[MapboxMarker] = None
  var end: Option[MapboxMarker] = None

  def toggleState(): Unit = state(!isEnabled)

  def state(enabled: Boolean): Unit = {
    if (enabled) {
      isEnabled = true
      mapContainer.classList.add(Routes)
      routesContainer.classList.add(Enabled)
      elemAs[HTMLSpanElement](Question).foreach(_.classList.add(Invisible))
    } else {
      isEnabled = false
      mapContainer.classList.remove(Routes)
      routesContainer.classList.remove(Enabled)
      clear()
    }
  }

  def updatePath(e: MapMouseEvent) = {
    if (isEnabled) {
      val c = Coord.buildOrFail(e.lngLat.lng, e.lngLat.lat)
      def endMarker = MapboxMarker(finishMark(c), c, map)
      (start, end) match {
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
      }
    }
  }

  def findRoute(from: Coord, to: Coord) =
    HttpClient.get[RouteResult](s"/routes/${from.lat}/${from.lng}/${to.lat}/${to.lng}").map { res =>
      val route = res.route
      val coords = route.coords
      val coll = FeatureCollection(
        Seq(Feature(LineGeometry(coords), Map(RouteSpec.Cost -> route.cost.asJson)))
      )
      elemGet[HTMLSpanElement](RouteLength).innerHTML = s"${formatDistance(route.cost)} km"
      drawLine(routeLayer, coll)
      coords.headOption.map { start =>
        val init = lineFor(Seq(from, start))
        drawLine(routeFirstLayer, init, LinePaint.dashed())
      }
      coords.lastOption.foreach { end =>
        val tail = lineFor(Seq(end, to))
        drawLine(routeLastLayer, tail, LinePaint.dashed())
      }
    }

  def clear(): Unit = {
    start.foreach(_.remove())
    start = None
    end.foreach(_.remove())
    end = None
    layerIds.foreach { id =>
      map.removeLayerAndSourceIfExists(id)
    }
  }

  def startMark(c: Coord) = span(`class` := "marker start")

  def finishMark(c: Coord) = span(`class` := "marker finish")
}
