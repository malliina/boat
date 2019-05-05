package com.malliina.boat

import com.malliina.http.HttpClient
import com.malliina.mapbox.{MapMouseEvent, MapboxMap, MapboxMarker}
import play.api.libs.json.Json
import scalatags.JsDom.all._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PathFinder {
  def apply(map: MapboxMap): PathFinder = new PathFinder(map)
}

class PathFinder(val map: MapboxMap) extends GeoUtils {
  var isEnabled: Boolean = false
  var start: Option[MapboxMarker] = None
  var end: Option[MapboxMarker] = None

  def toggleState() = state(!isEnabled)

  def state(enabled: Boolean): Unit = {
    if (enabled) {
      isEnabled = true
    } else {
      isEnabled = false
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

  def findRoute(from: Coord, to: Coord) = {
    HttpClient.get[RouteResult](s"/routes/${from.lat}/${from.lng}/${to.lat}/${to.lng}").map { res =>
      val route = res.route
      val coords = route.coords
      val coll = FeatureCollection(
        Seq(Feature(LineGeometry(coords),
                    Map(RouteSpec.Cost -> Json.toJson(route.cost)))))
      drawLine("route", coll)
      coords.headOption.map { start =>
        val init = lineFor(Seq(from, start))
        drawLine("route-first", init, LinePaint.dashed())
      }
      coords.lastOption.foreach { end =>
        val init = lineFor(Seq(end, to))
        drawLine("route-last", init, LinePaint.dashed())
      }
    }
  }

  def clear(): Unit = {
    start.foreach(_.remove())
    start = None
    end.foreach(_.remove())
    end = None
  }

  def startMark(c: Coord) = span(`class` := "marker start")

  def finishMark(c: Coord) = span(`class` := "marker finish")
}
