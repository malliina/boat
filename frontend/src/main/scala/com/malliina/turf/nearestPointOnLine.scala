package com.malliina.turf

import com.malliina.geojson.{GeoLineString, GeoPoint}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Module @turf/nearest-point-on-line exports a single function, this imports it.
  *
  * The function is callable from Scala using the apply method. I didn't get this to work using a
  * JSImport.Namespace import.
  */
@js.native
@JSImport("@turf/nearest-point-on-line", JSImport.Default)
object nearestPointOnLine extends js.Object {
//  def center(geoJson: js.Any): js.Any = js.native
//  def point(coord: js.Array[Double]): GeoPoint = js.native
//  def lineString(coords: js.Array[js.Array[Double]]): GeoLineString = js.native
//  def length(line: js.Any): Double = js.native

  /** Returns Feature <Point> - closest point on the line to point.
    *
    * The properties object will contain three values:
    * index : closest point was found on nth line part,
    * dist : distance between pt and the closest point,
    * location : distance along the line between start and the closest point.
    */
  def apply(line: GeoLineString, point: GeoPoint): NearestResult = js.native
}

@js.native
trait NearestResult extends js.Object {
  def properties: TurfPointResult = js.native
}

@js.native
trait TurfPointResult extends js.Object {
  def index: Int = js.native
  def dist: Double = js.native
  def location: Double = js.native
}
