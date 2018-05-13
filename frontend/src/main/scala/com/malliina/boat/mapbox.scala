package com.malliina.boat

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("mapboxgl")
object mapboxGl extends js.Object {
  var accessToken: String = js.native
}

@js.native
@JSGlobal("mapboxgl.Map")
class MapboxMap(options: MapOptions) extends js.Object {
  def flyTo(options: FlyOptions): Unit = js.native

  def easeTo(options: EaseOptions): Unit = js.native

  def getSource(id: String): js.UndefOr[GeoJsonSource] = js.native

  def addLayer(layer: js.Any): Unit = js.native

  def on(name: String, func: js.Function1[MouseEvent, Unit]): Unit = js.native

  def on(name: String, func: js.Function0[Unit]): Unit = js.native
}

@js.native
trait FlyOptions extends EaseOptions {
  def speed: Double = js.native
}

object FlyOptions {
  def apply(center: Coord, speed: Double): FlyOptions =
    literal(center = LngLat(center.lng, center.lat), speed = speed).asInstanceOf[FlyOptions]
}

@js.native
trait EaseOptions extends js.Object {
  def center: LngLat = js.native
}

object EaseOptions {
  def apply(center: Coord): EaseOptions =
    literal(center = LngLat(center.lng, center.lat)).asInstanceOf[EaseOptions]
}

@js.native
trait GeoJsonSource extends js.Object {
  def setData(data: js.Any): Unit = js.native
}

@js.native
trait LngLat extends js.Object {
  def lng: Double = js.native

  def lat: Double = js.native
}

object LngLat {
  def apply(lng: Double, lat: Double) =
    literal(lng = lng, lat = lat).asInstanceOf[LngLat]
}

@js.native
trait MouseEvent extends js.Object {
  def lngLat: LngLat = js.native
}

@js.native
trait MapOptions extends js.Object {
  def container: String = js.native

  def style: String = js.native

  def center: js.Array[Double] = js.native

  def zoom: Double = js.native
}

object MapOptions {
  def apply(container: String, style: String, center: Coord, zoom: Double) =
    literal(container = container, style = style, center = Seq(center.lng, center.lat).toJSArray, zoom = zoom).asInstanceOf[MapOptions]
}
