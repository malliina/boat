package com.malliina.mapbox

import com.malliina.boat.{
  AccessToken,
  Coord,
  Feature,
  FeatureCollection,
  JsonError,
  Latitude,
  Layer,
  Longitude,
  Parsing
}
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
trait GeocoderOptions extends js.Object {
  def accessToken: String
  def countries: String
  def mapboxgl: js.UndefOr[js.Object]
}

object GeocoderOptions {
  def apply(accessToken: String, countries: Seq[String], mapboxgl: Option[js.Object]): GeocoderOptions =
    literal(accessToken = accessToken, countries = countries.mkString(","), mapboxgl = mapboxgl.orUndefined)
      .asInstanceOf[GeocoderOptions]
}

@js.native
@JSImport("@mapbox/mapbox-gl-geocoder", JSImport.Default)
class MapboxGeocoder(options: GeocoderOptions) extends js.Object {
  def clear: Unit = js.native
}

object MapboxGeocoder {
  def finland(accessToken: AccessToken): MapboxGeocoder =
    new MapboxGeocoder(GeocoderOptions(accessToken.token, Seq("fi"), Option(mapboxGl)))
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
  def setLngLat(coord: LngLatLike): MapboxMarker = js.native
  def getLngLat(): LngLat = js.native
  def setPopup(popup: MapboxPopup): MapboxMarker = js.native
  def addTo(map: MapboxMap): MapboxMarker = js.native
  def remove(): Unit = js.native
}

object MapboxMarker {

  def apply[T <: dom.Element](html: TypedTag[T],
                              coord: Coord,
                              popup: MapboxPopup,
                              on: MapboxMap): MapboxMarker =
    new MapboxMarker(MarkerOptions(html)).at(coord).setPopup(popup).addTo(on)

  def apply[T <: dom.Element](html: TypedTag[T], coord: Coord, on: MapboxMap): MapboxMarker =
    new MapboxMarker(MarkerOptions(html)).at(coord).addTo(on)

  implicit class MarkerExt(val self: MapboxMarker) extends AnyVal {
    def at(coord: Coord): MapboxMarker = self.setLngLat(LngLatLike(coord.lng, coord.lat))
    def coord: Coord = {
      val lngLat = self.getLngLat()
      Coord(Longitude(lngLat.lng), Latitude(lngLat.lat))
    }
  }

}

@js.native
trait PopupOptions extends js.Object {
  def className: js.UndefOr[String] = js.native

  def offset: js.UndefOr[Double] = js.native

  def closeButton: Boolean = js.native
}

object PopupOptions {
  def apply(className: Option[String] = None,
            offset: Option[Double] = None,
            closeButton: Boolean = false): PopupOptions =
    literal(className = className.orUndefined,
            offset = offset.orUndefined,
            closeButton = closeButton)
      .asInstanceOf[PopupOptions]
}

@js.native
@JSImport("mapbox-gl", "Popup")
class MapboxPopup(options: PopupOptions) extends js.Object {
  def setLngLat(coord: LngLatLike): MapboxPopup = js.native
  def setHTML(html: String): MapboxPopup = js.native
  def setText(text: String): MapboxPopup = js.native
  def addTo(map: MapboxMap): MapboxPopup = js.native
  def remove(): Unit = js.native
}

object MapboxPopup {
  def apply(options: PopupOptions): MapboxPopup = new MapboxPopup(options)

  implicit class PopupExt(val self: MapboxPopup) extends AnyVal {
    def show[T <: dom.Element](htmlPayload: TypedTag[T], coord: LngLatLike, on: MapboxMap): Unit =
      html(htmlPayload).setLngLat(coord).addTo(on)

    def html[T <: dom.Element](html: TypedTag[T]): MapboxPopup =
      self.setHTML(html.render.outerHTML)

    def showText(text: String, coord: LngLatLike, on: MapboxMap): Unit =
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
  def addControl(control: MapboxGeocoder): MapboxMap = js.native
  def removeControl(control: MapboxGeocoder): MapboxMap = js.native
  def flyTo(options: FlyOptions): Unit = js.native
  def easeTo(options: EaseOptions): Unit = js.native
  def fitBounds(bounds: LngLatBounds, options: SimplePaddingOptions): Unit = js.native
  def loadImage(url: String, callback: js.Function2[js.Any, js.Any, Unit]): Unit = js.native
  def addImage(id: String, image: js.Any): Unit = js.native
  def getSource(id: String): js.UndefOr[GeoJsonSource] = js.native
  def addLayer(layer: js.Any): Unit = js.native
  def removeLayer(id: String): Unit = js.native
  def removeSource(id: String): Unit = js.native
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

    def removeLayerAndSourceIfExists(id: String): Unit = {
      self.findSource(id).foreach { _ =>
        self.removeLayer(id)
        self.removeSource(id)
      }
    }

    def queryRendered(point: PixelCoord,
                      options: QueryOptions = QueryOptions.all): Either[JsonError, Seq[Feature]] = {
      val fs = self.queryRenderedFeatures(point, options)
      Parsing.asJson[Seq[Feature]](fs)
    }

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
class LngLatBounds(sw: LngLatLike, ne: LngLatLike) extends js.Object {
  def extend(bounds: LngLatBounds): LngLatBounds = js.native
  def extend(bounds: LngLat): LngLatBounds = js.native
  def isEmpty: Boolean = js.native
  def getSouthWest(): LngLatLike = js.native
  def getNorthEast(): LngLatLike = js.native
  def getNorthWest(): LngLatLike = js.native
  def getSouthEast(): LngLatLike = js.native
}

object LngLatBounds {
  def apply(coord: Coord): LngLatBounds = {
    val sw = LngLat(coord)
    val ne = LngLat(coord)
    new LngLatBounds(sw, ne)
  }

  implicit class LngLatBoundsExt(val self: LngLatBounds) extends AnyVal {
    def extendWith(coord: Coord): LngLatBounds =
      self.extend(LngLat(coord))
  }

}

@js.native
trait SimplePaddingOptions extends js.Object {
  def padding: Int = js.native
}

object SimplePaddingOptions {
  def apply(padding: Int) = literal(padding = padding).asInstanceOf[SimplePaddingOptions]
}

@js.native
trait PaddingOptions extends js.Object {
  def top: Int = js.native
  def right: Int = js.native
  def bottom: Int = js.native
  def left: Int = js.native
}

object PaddingOptions {
  def apply(all: Int): PaddingOptions = apply(all, all, all, all)

  def apply(top: Int, right: Int, bottom: Int, left: Int): PaddingOptions =
    literal(top = top, right = right, bottom = bottom, left = left).asInstanceOf[PaddingOptions]
}

@js.native
trait FitOptions extends js.Object {
  def padding: PaddingOptions = js.native
  def linear: Boolean = js.native
  def maxZoom: js.UndefOr[Double] = js.native
}

object FitOptions {
  def apply(padding: Int, linear: Boolean = false, maxZoom: Option[Double] = None): FitOptions =
    literal(padding = PaddingOptions(padding), linear = linear, maxZoom = maxZoom.orUndefined)
      .asInstanceOf[FitOptions]
}

@js.native
trait FlyOptions extends EaseOptions {
  def speed: Double = js.native
}

object FlyOptions {
  val SpeedDefault: Double = 1.2d

  def apply(center: Coord, speed: Double = SpeedDefault): FlyOptions =
    literal(center = LngLatLike(center), speed = speed).asInstanceOf[FlyOptions]
}

@js.native
trait EaseOptions extends js.Object {
  def center: LngLatLike = js.native
}

object EaseOptions {
  def apply(center: Coord): EaseOptions =
    literal(center = LngLatLike(center)).asInstanceOf[EaseOptions]
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
@JSImport("mapbox-gl", "LngLat")
class LngLat(lng: Double, lat: Double) extends LngLatLike

object LngLat {
  def apply(coord: Coord): LngLat = new LngLat(coord.lng.lng, coord.lat.lat)
}

@js.native
trait LngLatLike extends js.Object {
  def lng: Double = js.native
  def lat: Double = js.native
}

object LngLatLike {
  def apply(lng: Longitude, lat: Latitude): LngLatLike =
    literal(lng = lng.lng, lat = lat.lat).asInstanceOf[LngLatLike]

  def apply(coord: Coord): LngLatLike = apply(coord.lng, coord.lat)
}

@js.native
trait MapMouseEvent extends js.Object {
  def lngLat: LngLatLike = js.native
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
  def apply(container: String,
            style: String,
            center: Coord,
            zoom: Double,
            hash: Boolean = false): MapOptions =
    literal(
      container = container,
      style = style,
      center = Seq(center.lng, center.lat).toJSArray,
      zoom = zoom,
      hash = hash
    ).asInstanceOf[MapOptions]
}
