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
      def endMarker = MapboxMarker(endMark(c), c, map)
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
      val coll = FeatureCollection(
        Seq(Feature(LineGeometry(route.links.map(_.to)),
                    Map(RouteSpec.Cost -> Json.toJson(route.cost)))))
      drawLine("route", coll)
    }
  }

  def clear(): Unit = {
    start.foreach(_.remove())
    start = None
    end.foreach(_.remove())
    end = None
  }

  def startMark(c: Coord) = span(s"Start at $c")
  def endMark(c: Coord) = span(s"Finish at $c")
}
