package com.malliina.turf

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("turf")
object turf extends js.Object {
  def center(geoJson: js.Any): js.Any = js.native

  def point(coord: js.Array[Double]): js.Any = js.native

  def lineString(coords: js.Array[js.Array[Double]]): js.Any = js.native

  def length(line: js.Any): Double = js.native

  def nearestPointOnLine(line: js.Any, point: js.Any): js.Any = js.native
}
