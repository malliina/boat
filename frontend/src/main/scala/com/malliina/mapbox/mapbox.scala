package com.malliina.mapbox

import cats.effect.Async
import cats.syntax.all.toFunctorOps
import com.malliina.boat.{AccessToken, Coord, Feature, FeatureCollection, Latitude, Layer, Longitude}
import com.malliina.geojson.GeoFeature
import com.malliina.http.FullUrl
import com.malliina.json.JsonError
import com.malliina.json.Parsing.{as, asJs}
import org.scalajs.dom
import org.scalajs.dom.{HTMLCanvasElement, html}
import scalatags.JsDom.TypedTag

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.{JSImport, JSName}

@js.native
@JSImport("mapbox-gl", JSImport.Default)
object mapboxGl extends js.Object:
  var accessToken: String = js.native

@js.native
trait GeocoderOptions extends js.Object:
  def accessToken: String
  def countries: String
  def mapboxgl: js.UndefOr[js.Object]

object GeocoderOptions:
  def apply(
    accessToken: String,
    countries: Seq[String],
    mapboxgl: Option[js.Object]
  ): GeocoderOptions =
    literal(
      accessToken = accessToken,
      countries = countries.mkString(","),
      mapboxgl = mapboxgl.orUndefined
    ).asInstanceOf[GeocoderOptions]

@js.native
@JSImport("@mapbox/mapbox-gl-geocoder", JSImport.Default)
class MapboxGeocoder(@unused options: GeocoderOptions) extends js.Object:
  def clear: Unit = js.native

object MapboxGeocoder:
  def finland(accessToken: AccessToken): MapboxGeocoder =
    MapboxGeocoder(GeocoderOptions(accessToken.token, Seq("fi"), Option(mapboxGl)))

@js.native
trait MarkerOptions extends js.Object:
  def element: html.Element

object MarkerOptions:
  def apply[T <: dom.Element](html: TypedTag[T]): MarkerOptions =
    apply(html.render)

  def apply[T <: dom.Element](el: T): MarkerOptions =
    literal(element = el).asInstanceOf[MarkerOptions]

@js.native
@JSImport("mapbox-gl", "Marker")
class MapboxMarker(@unused options: MarkerOptions) extends js.Object:
  def setLngLat(coord: LngLatLike): MapboxMarker = js.native
  def getLngLat(): LngLat = js.native
  def setPopup(popup: MapboxPopup): MapboxMarker = js.native
  def addTo(map: MapboxMap): MapboxMarker = js.native
  def remove(): Unit = js.native

object MapboxMarker:
  def apply[T <: dom.Element](
    html: TypedTag[T],
    coord: Coord,
    popup: MapboxPopup,
    on: MapboxMap
  ): MapboxMarker =
    new MapboxMarker(MarkerOptions(html)).at(coord).setPopup(popup).addTo(on)

  def apply[T <: dom.Element](html: TypedTag[T], coord: Coord, on: MapboxMap): MapboxMarker =
    new MapboxMarker(MarkerOptions(html)).at(coord).addTo(on)

  extension (self: MapboxMarker)
    def at(coord: Coord): MapboxMarker =
      self.setLngLat(LngLatLike(coord.lng, coord.lat))
    def coord: Coord =
      val lngLat = self.getLngLat()
      Coord(Longitude.unsafe(lngLat.lng), Latitude.unsafe(lngLat.lat))

@js.native
trait PopupOptions extends js.Object:
  def className: js.UndefOr[String] = js.native
  def offset: js.UndefOr[Double] = js.native
  def closeButton: Boolean = js.native
  def maxWidth: String = js.native

object PopupOptions:
  def apply(
    className: Option[String] = None,
    offset: Option[Double] = None,
    closeButton: Boolean = false,
    maxWidth: String = "240px"
  ): PopupOptions =
    literal(
      className = className.orUndefined,
      offset = offset.orUndefined,
      closeButton = closeButton,
      maxWidth = maxWidth
    ).asInstanceOf[PopupOptions]

@js.native
@JSImport("mapbox-gl", "Popup")
class MapboxPopup(@unused options: PopupOptions) extends js.Object:
  def setLngLat(coord: LngLatLike): MapboxPopup = js.native
  def setHTML(html: String): MapboxPopup = js.native
  def setText(text: String): MapboxPopup = js.native
  def addTo(map: MapboxMap): MapboxPopup = js.native
  def remove(): Unit = js.native
  def isOpen(): Boolean = js.native
  def setMaxWidth(v: String): MapboxPopup = js.native

object MapboxPopup:
  extension (self: MapboxPopup)
    def show[T <: dom.Element](htmlPayload: TypedTag[T], coord: LngLatLike, on: MapboxMap): Unit =
      html(htmlPayload).setLngLat(coord).setMaxWidth("none").addTo(on)
    def show2[T <: dom.Element](htmlPayload: TypedTag[T], coord: LngLatLike, on: MapboxMap): Unit =
      html(htmlPayload).setLngLat(coord).addTo(on)
    def html[T <: dom.Element](html: TypedTag[T]): MapboxPopup =
      self.setHTML(html.render.outerHTML)
    def showText(text: String, coord: LngLatLike, on: MapboxMap): Unit =
      self.setText(text).setLngLat(coord).addTo(on)

@js.native
trait QueryOptions extends js.Object:
  def layers: js.UndefOr[js.Array[String]] = js.native

object QueryOptions:
  def layer(id: String) = apply(Seq(id))
  def apply(layers: Seq[String]): QueryOptions =
    literal(layers = layers.toJSArray).asInstanceOf[QueryOptions]
  def all: QueryOptions = literal().asInstanceOf[QueryOptions]

@js.native
@JSImport("mapbox-gl", "Map")
class MapboxMap(@unused options: MapOptions) extends js.Object:
  def addControl(control: MapboxGeocoder): MapboxMap = js.native
  def removeControl(control: MapboxGeocoder): MapboxMap = js.native
  def flyTo(options: FlyOptions): Unit = js.native
  def easeTo(options: EaseOptions): Unit = js.native
  def fitBounds(bounds: LngLatBounds, options: SimplePaddingOptions): Unit = js.native
  def loadImage(url: String, callback: js.Function2[js.Any, js.Any, Unit]): Unit = js.native
  def addImage(id: String, image: js.Any): Unit = js.native
  def addSource(id: String, source: js.Any): Unit = js.native
  def getSource(id: String): js.UndefOr[GeoJsonSource] = js.native
  def addLayer(layer: js.Any): Unit = js.native
  def getLayer(id: String): js.UndefOr[js.Any] = js.native
  def removeLayer(id: String): Unit = js.native
  def removeSource(id: String): Unit = js.native
  def setLayoutProperty(layer: String, prop: String, value: js.Any): Unit = js.native
  def setPaintProperty(layer: String, prop: String, value: js.Any): Unit = js.native
  def getPaintProperty(layer: String, prop: String): js.UndefOr[js.Any] = js.native
  def queryRenderedFeatures(point: PixelCoord, options: QueryOptions): js.Any = js.native
  def getCanvas(): HTMLCanvasElement = js.native
  def getCenter(): LngLat = js.native
  def getZoom(): Double = js.native

  /** The bearing is the compass direction that is "up".
    *
    * <ul> <li>A bearing of 90° orients the map so that east is up.</li> <li>A bearing of -90°
    * orients the map so that west is up.</li> </ul>
    *
    * @return
    *   Returns the map's current bearing.
    * @see
    *   https://docs.mapbox.com/mapbox-gl-js/api/#map#getbearing
    */
  def getBearing(): Double = js.native
  def on(name: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native
  def on(name: String, func: js.Function0[Unit]): Unit = js.native
  def on(name: String, layer: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native
  def off(name: String, layer: String, func: js.Function1[MapMouseEvent, Unit]): Unit = js.native
  def on(name: String, layer: String, func: js.Function0[Unit]): Unit = js.native
  def off(name: String, layer: String, func: js.Function0[Unit]): Unit = js.native

object MapboxMap:
  extension (self: MapboxMap)
    def bearing = self.getBearing()

    def putLayer(layer: Layer): Unit =
      self.addLayer(layer.asJs)

    def removeLayerAndSourceIfExists(id: String): Unit =
      self
        .findSource(id)
        .foreach: _ =>
          self.removeLayer(id)
          self.removeSource(id)

    def queryRendered(
      point: PixelCoord,
      options: QueryOptions = QueryOptions.all
    ): Either[JsonError, Seq[Feature]] =
      self.queryRenderedFeatures(point, options).as[Seq[Feature]]

    def onHover(layerId: String)(in: MapMouseEvent => Unit, out: MapMouseEvent => Unit): Unit =
      self.on("mousemove", layerId, e => in(e))
      self.on("mouseleave", layerId, e => out(e))

    def onHoverEnter(layerId: String)(in: MapMouseEvent => Unit, out: MapMouseEvent => Unit): Unit =
      self.on("mouseenter", layerId, e => in(e))
      self.on("mouseleave", layerId, e => out(e))

    def onHoverCursorPointer(layerId: String): Unit = onHover(layerId)(
      in => self.getCanvas().style.cursor = "pointer",
      out => self.getCanvas().style.cursor = ""
    )

    def initImage[F[_]: Async](uri: String, iconId: String): F[Unit] =
      fetchImage(uri).map: image =>
        self.addImage(iconId, image)

    def fetchImage[F[_]: Async](uri: String): F[js.Any] =
      Async[F].async_[js.Any]: cb =>
        self.loadImage(
          uri,
          (err, data) =>
            if err == null then cb(Right(data))
            else cb(Left(new Exception(s"Failed to load '$uri'.")))
        )

    def findSource(id: String): Option[GeoJsonSource] = self.getSource(id).toOption
    def hasSource(id: String): Boolean = findSource(id).isDefined
    def hasLayer(id: String): Boolean = self.getLayer(id).toOption.isDefined

@js.native
@JSImport("mapbox-gl", "LngLatBounds")
class LngLatBounds(@unused sw: LngLatLike, @unused ne: LngLatLike) extends js.Object:
  def extend(bounds: LngLatBounds): LngLatBounds = js.native
  def extend(bounds: LngLat): LngLatBounds = js.native
  def isEmpty: Boolean = js.native
  def getSouthWest(): LngLatLike = js.native
  def getNorthEast(): LngLatLike = js.native
  def getNorthWest(): LngLatLike = js.native
  def getSouthEast(): LngLatLike = js.native

object LngLatBounds:
  def apply(coord: Coord): LngLatBounds =
    val sw = LngLat(coord)
    val ne = LngLat(coord)
    new LngLatBounds(sw, ne)

  implicit class LngLatBoundsExt(val self: LngLatBounds) extends AnyVal:
    def extendWith(coord: Coord): LngLatBounds =
      self.extend(LngLat(coord))

@js.native
trait SimplePaddingOptions extends js.Object:
  def padding: Int = js.native

object SimplePaddingOptions:
  def apply(padding: Int) = literal(padding = padding).asInstanceOf[SimplePaddingOptions]

@js.native
trait PaddingOptions extends js.Object:
  def top: Int = js.native
  def right: Int = js.native
  def bottom: Int = js.native
  def left: Int = js.native

object PaddingOptions:
  def apply(all: Int): PaddingOptions = apply(all, all, all, all)

  def apply(top: Int, right: Int, bottom: Int, left: Int): PaddingOptions =
    literal(top = top, right = right, bottom = bottom, left = left).asInstanceOf[PaddingOptions]

@js.native
trait FitOptions extends js.Object:
  def padding: PaddingOptions = js.native
  def linear: Boolean = js.native
  def maxZoom: js.UndefOr[Double] = js.native

object FitOptions:
  def apply(padding: Int, linear: Boolean = false, maxZoom: Option[Double] = None): FitOptions =
    literal(padding = PaddingOptions(padding), linear = linear, maxZoom = maxZoom.orUndefined)
      .asInstanceOf[FitOptions]

@js.native
trait FlyOptions extends EaseOptions:
  def speed: Double = js.native

object FlyOptions:
  val SpeedDefault: Double = 1.2d

  def apply(center: Coord, speed: Double = SpeedDefault): FlyOptions =
    literal(center = LngLatLike(center), speed = speed).asInstanceOf[FlyOptions]

@js.native
trait EaseOptions extends js.Object:
  def center: LngLatLike = js.native

object EaseOptions:
  def apply(center: Coord): EaseOptions =
    literal(center = LngLatLike(center)).asInstanceOf[EaseOptions]

@js.native
trait GeoJsonSource extends js.Object:
  @JSName("type")
  def `type`: String = js.native
  def data: String = js.native
  def setData(data: js.Any): Unit = js.native

object GeoJsonSource:
  def apply(url: FullUrl): GeoJsonSource =
    literal(`type` = "geojson", data = url.url).asInstanceOf[GeoJsonSource]
  extension (source: GeoJsonSource)
    def updateData(data: FeatureCollection): Unit =
      source.setData(data.asJs)

@js.native
@JSImport("mapbox-gl", "LngLat")
class LngLat(@unused lng: Double, @unused lat: Double) extends LngLatLike

object LngLat:
  def apply(coord: Coord): LngLat = new LngLat(coord.lng.lng, coord.lat.lat)
  def coord(lngLat: LngLat): Coord =
    Coord(lng = Longitude.unsafe(lngLat.lng), lat = Latitude.unsafe(lngLat.lat))

@js.native
trait LngLatLike extends js.Object:
  def lng: Double = js.native
  def lat: Double = js.native

object LngLatLike:
  def apply(lng: Longitude, lat: Latitude): LngLatLike =
    literal(lng = lng.lng, lat = lat.lat).asInstanceOf[LngLatLike]

  def apply(coord: Coord): LngLatLike = apply(coord.lng, coord.lat)

@js.native
trait MapMouseEvent extends js.Object:
  def lngLat: LngLatLike = js.native
  def point: PixelCoord = js.native
  def features: js.UndefOr[js.Array[GeoFeature[?]]] = js.native

object MapMouseEvent:
  extension (mme: MapMouseEvent)
    def featuresSeq: Seq[GeoFeature[?]] = mme.features.toList.flatMap(_.toList)

@js.native
trait PixelCoord extends js.Object:
  def x: Int = js.native
  def y: Int = js.native

@js.native
trait MapOptions extends js.Object:
  def container: String = js.native
  def style: String = js.native
  def center: js.Array[Double] = js.native
  def zoom: Double = js.native
  def hash: Boolean = js.native

object MapOptions:
  def apply(
    container: String,
    style: String,
    center: Coord,
    zoom: Double,
    hash: Boolean = false
  ): MapOptions =
    literal(
      container = container,
      style = style,
      center = Seq(center.lng, center.lat).toJSArray,
      zoom = zoom,
      hash = hash
    ).asInstanceOf[MapOptions]
