package com.malliina.mapbox

import com.malliina.boat.{Coord, Feature, FeatureCollection, JsonError, Layer, Parsing}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.HTMLCanvasElement
import scalatags.JsDom.TypedTag

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("mapbox-gl", JSImport.Default)
object mapboxGl extends js.Object {
  var accessToken: String = js.native
}

@js.native
trait MarkerOptions extends js.Object {
  def element: html.Element
}

object MarkerOptions {
  def apply[T <: dom.Element](html: TypedTag[T]): MarkerOptions =
    literal(element = html.render).asInstanceOf[MarkerOptions]
}

@js.native
@JSImport("mapbox-gl", "Marker")
class MapboxMarker(options: MarkerOptions) extends js.Object {
  def setLngLat(coord: LngLat): MapboxMarker = js.native

  def setPopup(popup: MapboxPopup): MapboxMarker = js.native

  def addTo(map: MapboxMap): MapboxMarker = js.native

  def remove(): Unit = js.native
}

object MapboxMarker {

  def apply[T <: dom.Element](html: TypedTag[T], coord: Coord, popup: MapboxPopup, on: MapboxMap) =
    new MapboxMarker(MarkerOptions(html)).coord(coord).setPopup(popup).addTo(on)

  implicit class MarkerExt(val self: MapboxMarker) extends AnyVal {
    def coord(coord: Coord): MapboxMarker = self.setLngLat(LngLat(coord.lng, coord.lat))
  }

}

@js.native
trait PopupOptions extends js.Object {
  def className: js.UndefOr[String] = js.native

  def offset: js.UndefOr[Double] = js.native

  def closeButton: Boolean = js.native
}

object PopupOptions {
  def apply(className: Option[String] = None, offset: Option[Double] = None, closeButton: Boolean = false): PopupOptions =
    literal(className = className.orUndefined, offset = offset.orUndefined, closeButton = closeButton)
      .asInstanceOf[PopupOptions]
}

@js.native
@JSImport("mapbox-gl", "Popup")
class MapboxPopup(options: PopupOptions) extends js.Object {
  def setLngLat(coord: LngLat): MapboxPopup = js.native

  def setHTML(html: String): MapboxPopup = js.native

  def setText(text: String): MapboxPopup = js.native

  def addTo(map: MapboxMap): MapboxPopup = js.native

  def remove(): Unit = js.native
}

object MapboxPopup {
  def apply(options: PopupOptions): MapboxPopup = new MapboxPopup(options)

  implicit class PopupExt(val self: MapboxPopup) extends AnyVal {
    def show[T <: dom.Element](htmlPayload: TypedTag[T], coord: LngLat, on: MapboxMap): Unit =
      html(htmlPayload).setLngLat(coord).addTo(on)

    def html[T <: dom.Element](html: TypedTag[T]): MapboxPopup =
      self.setHTML(html.render.outerHTML)

    def showText(text: String, coord: LngLat, on: MapboxMap): Unit =
      self.setText(text).setLngLat(coord).addTo(on)
  }

}

@js.native
trait QueryOptions extends js.Object {
  def layers: js.UndefOr[js.Array[String]] = js.native
}

object QueryOptions {
  def layer(id: String) = apply(Seq(id))

  def apply(layers: Seq[String]): QueryOptions =
    literal(layers = layers.toJSArray).asInstanceOf[QueryOptions]

  def all: QueryOptions = literal().asInstanceOf[QueryOptions]
}

@js.native
@JSImport("mapbox-gl", "Map")
class MapboxMap(options: MapOptions) extends js.Object {
  def flyTo(options: FlyOptions): Unit = js.native

  def easeTo(options: EaseOptions): Unit = js.native

  def fitBounds(bounds: LngLatBounds, options: FitOptions): Unit = js.native

  def loadImage(url: String, callback: js.Function2[js.Any, js.Any, Unit]): Unit = js.native

  def addImage(id: String, image: js.Any): Unit = js.native

  def getSource(id: String): js.UndefOr[GeoJsonSource] = js.native

  def addLayer(layer: js.Any): Unit = js.native

  def setLayoutProperty(layer: String, prop: String, value: js.Any): Unit = js.native

  def queryRenderedFeatures(point: PixelCoord, options: QueryOptions): js.Any = js.native

  def getCanvas(): HTMLCanvasElement = js.native

  /** The bearing is the compass direction that is "up".
    *
    * <ul>
    *   <li>A bearing of 90° orients the map so that east is up.</li>
    *   <li>A bearing of -90° orients the map so that west is up.</li>
    * </ul>
    *
    * @return Returns the map's current bearing.
    * @see https://docs.mapbox.com/mapbox-gl-js/api/#map#getbearing
    */
  def getBearing(): Double = js.native

  def on(name: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native

  def on(name: String, func: js.Function0[Unit]): Unit = js.native

  def on(name: String, layer: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native

  def on(name: String, layer: String, func: js.Function0[Unit]): Unit = js.native
}

object MapboxMap {

  implicit class MapExt(val self: MapboxMap) extends AnyVal {
    def bearing = self.getBearing()

    def putLayer(layer: Layer): Unit =
      self.addLayer(JSON.parse(Parsing.stringify(layer)))

    def queryRendered(point: PixelCoord, options: QueryOptions = QueryOptions.all): Either[JsonError, Seq[Feature]] =
      Parsing.asJson[Seq[Feature]](self.queryRenderedFeatures(point, options))

    def onHover(layerId: String)(in: MapMouseEvent => Unit, out: MapMouseEvent => Unit): Unit = {
      self.on("mousemove", layerId, e => in(e))
      self.on("mouseleave", layerId, e => out(e))
    }

    def initImage(uri: String, iconId: String): Future[Unit] =
      fetchImage(uri).map { image =>
        self.addImage(iconId, image)
      }

    def fetchImage(uri: String): Future[js.Any] = {
      val p = Promise[js.Any]()
      self.loadImage(uri, (err, data) => {
        if (err == null) p.success(data)
        else p.failure(new Exception(s"Failed to load '$uri'."))
      })
      p.future
    }

    def findSource(id: String): Option[GeoJsonSource] = self.getSource(id).toOption
  }

}

@js.native
@JSImport("mapbox-gl", "LngLatBounds")
class LngLatBounds(sw: js.Array[Double], ne: js.Array[Double]) extends js.Object {
  def extend(coord: js.Array[Double]): LngLatBounds = js.native
}

object LngLatBounds {
  def apply(coord: Coord): LngLatBounds =
    new LngLatBounds(coord.toArray.toJSArray, coord.toArray.toJSArray)

  implicit class LngLatBoundsExt(val self: LngLatBounds) extends AnyVal {
    def extendWith(coord: Coord): LngLatBounds =
      self.extend(coord.toArray.toJSArray)
  }

}

@js.native
trait FitOptions extends js.Object {
  def padding: Double = js.native

  def linear: Boolean = js.native

  def maxZoom: js.UndefOr[Double] = js.native
}

object FitOptions {
  def apply(padding: Double, linear: Boolean = false, maxZoom: Option[Double] = None): FitOptions =
    literal(padding = padding, linear = linear, maxZoom = maxZoom.orUndefined).asInstanceOf[FitOptions]
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

object GeoJsonSource {

  implicit class GeoJsonSourceExt(val source: GeoJsonSource) extends AnyVal {

    def updateData(data: FeatureCollection): Unit =
      source.setData(Parsing.toJson(data))
  }

}

@js.native
trait LngLat extends js.Object {
  def lng: Double = js.native

  def lat: Double = js.native
}

object LngLat {
  def apply(lng: Double, lat: Double): LngLat =
    literal(lng = lng, lat = lat).asInstanceOf[LngLat]

  def apply(coord: Coord): LngLat = apply(coord.lng, coord.lat)
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