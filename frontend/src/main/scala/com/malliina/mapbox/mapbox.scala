package com.malliina.mapbox

import com.malliina.boat.Coord
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLCanvasElement
import play.api.libs.json.{Json, Writes}
import scalatags.JsDom.TypedTag

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("mapboxgl")
object mapboxGl extends js.Object {
  var accessToken: String = js.native
}

@js.native
@JSGlobal("mapboxgl.Popup")
class MapboxPopup(options: PopupOptions) extends js.Object {
  def setLngLat(coord: LngLat): MapboxPopup = js.native

  def setHTML(html: String): MapboxPopup = js.native

  def setText(text: String): MapboxPopup = js.native

  def addTo(map: MapboxMap): Unit = js.native

  def remove(): Unit = js.native
}

object MapboxPopup {

  implicit class PopupExt(val self: MapboxPopup) extends AnyVal {
    def show[T <: dom.Element](html: TypedTag[T], coord: LngLat, on: MapboxMap): Unit =
      self.setHTML(html.render.outerHTML).setLngLat(coord).addTo(on)

    def showText(text: String, coord: LngLat, on: MapboxMap): Unit =
      self.setText(text).setLngLat(coord).addTo(on)
  }

}

@js.native
@JSGlobal("mapboxgl.Map")
class MapboxMap(options: MapOptions) extends js.Object {
  def flyTo(options: FlyOptions): Unit = js.native

  def easeTo(options: EaseOptions): Unit = js.native

  def fitBounds(bounds: LngLatBounds, options: FitOptions): Unit = js.native

  def loadImage(url: String, callback: js.Function2[js.Any, js.Any, Unit]): Unit = js.native

  def addImage(id: String, image: js.Any): Unit = js.native

  def getSource(id: String): js.UndefOr[GeoJsonSource] = js.native

  def addLayer(layer: js.Any): Unit = js.native

  def setLayoutProperty(layer: String, prop: String, value: js.Any): Unit = js.native

  def queryRenderedFeatures(point: PixelCoord): js.Any = js.native

  def getCanvas(): HTMLCanvasElement = js.native

  def on(name: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native

  def on(name: String, func: js.Function0[Unit]): Unit = js.native

  def on(name: String, event: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native

  def on(name: String, event: String, func: js.Function0[Unit]): Unit = js.native
}

object MapboxMap {

  implicit class MapExt(val self: MapboxMap) extends AnyVal {
    def putLayer[T: Writes](t: T): Unit =
      self.addLayer(JSON.parse(Json.stringify(Json.toJson(t))))
  }

}

@js.native
@JSGlobal("mapboxgl.LngLatBounds")
class LngLatBounds(sw: js.Array[Double], ne: js.Array[Double]) extends js.Object {
  def extend(coord: js.Array[Double]): LngLatBounds = js.native
}

object LngLatBounds {
  def apply(coord: Coord): LngLatBounds =
    new LngLatBounds(coord.toArray.toJSArray, coord.toArray.toJSArray)
}

@js.native
trait FitOptions extends js.Object {
  def padding: Double = js.native

  def linear: Boolean = js.native

  def maxZoom: js.UndefOr[Int] = js.native
}

object FitOptions {
  def apply(padding: Double, linear: Boolean = false): FitOptions =
    literal(padding = padding, linear = linear).asInstanceOf[FitOptions]
}

@js.native
trait FlyOptions extends EaseOptions {
  def speed: Double = js.native
}

object FlyOptions {
  val SpeedDefault: Double = 1.2d

  def apply(center: Coord, speed: Double = SpeedDefault): FlyOptions =
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
trait MapMouseEvent extends js.Object {
  def lngLat: LngLat = js.native

  def point: PixelCoord = js.native

  def features: js.Any = js.native
}

@js.native
trait PixelCoord extends js.Object {
  def x: Int = js.native

  def y: Int = js.native
}

@js.native
trait MapOptions extends js.Object {
  def container: String = js.native

  def style: String = js.native

  def center: js.Array[Double] = js.native

  def zoom: Double = js.native

  def hash: Boolean = js.native
}

object MapOptions {
  def apply(container: String, style: String, center: Coord, zoom: Double, hash: Boolean = false): MapOptions =
    literal(
      container = container,
      style = style,
      center = Seq(center.lng, center.lat).toJSArray,
      zoom = zoom,
      hash = hash
    ).asInstanceOf[MapOptions]
}

@js.native
trait PopupOptions extends js.Object {
  def className: js.UndefOr[String] = js.native

  def offset: js.UndefOr[Double] = js.native

  def closeButton: Boolean = js.native
}

object PopupOptions {
  def apply(className: Option[String], offset: Option[Double], closeButton: Boolean): PopupOptions =
    literal(className = className.orUndefined, offset = offset.orUndefined, closeButton = closeButton)
      .asInstanceOf[PopupOptions]
}
