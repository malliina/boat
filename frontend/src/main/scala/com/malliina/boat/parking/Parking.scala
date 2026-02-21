package com.malliina.boat.parking

import cats.effect.Async
import cats.implicits.toFunctorOps
import com.malliina.boat.{BaseLogger, FrontKeys, GeoUtils, Lang, ParkingResponse, Popups}
import com.malliina.geo.Coord
import com.malliina.geojson.{Feature, FeatureCollection, LineGeometry}
import com.malliina.http.{FullUrl, Http}
import com.malliina.http.UrlSyntax.https
import com.malliina.json.Parsing.as
import com.malliina.mapbox.LngLat.coord
import com.malliina.mapbox.{FillPaint, GeoJsonSource, Layer, LayerType, LinePaint, MapboxMap, MapboxPopup, PopupOptions, StringLayerSource}
import io.circe.syntax.EncoderOps
import org.scalajs.dom.window

import scala.scalajs.js.JSON

class Parking[F[_]: Async](
  map: MapboxMap,
  language: Lang,
  val log: BaseLogger = BaseLogger.console,
  http: Http[F]
):
  val utils = GeoUtils(map, log)
  val location = window.location
  private val parkingsUrl: FullUrl =
    https"kartta.hel.fi/ws/geoserver/avoindata/wfs?request=getFeature&typeNames=avoindata:Pysakointipaikat_alue&outputFormat=application/json&srsName=EPSG:4326"

  val popup = MapboxPopup(PopupOptions())
  private val popupsHtml = Popups(language)
  map.on(
    "load",
    () =>
      installParkingAreas()
      installCapacity()
      installHoverListener()
  )
  private val capacityLayerId = "capacity-layer"
  private val parkingsLayerId = "parkings-layer"
  private val parkingDirectionsId = "parking-directions-layer"
  private val baseUrl = location.origin // .getOrElse(s"${location.protocol}//${location.host}")

  def search(from: Coord = coord(map.getCenter())): Unit =
    val uri = s"/cars/parkings/search?${FrontKeys.Lat}=${from.lat}&${FrontKeys.Lng}=${from.lng}"
    http.using: client =>
      client
        .get[ParkingResponse](uri)
        .map: res =>
          res.directions.headOption.map: best =>
            val f = Feature(
              LineGeometry(Seq(best.from, best.nearest.coord)),
              Map("distance" -> best.nearest.distance.asJson)
            )
            val fc = FeatureCollection(Seq(f))
            utils.drawLine(parkingDirectionsId, fc, LinePaint.dashed())

  private def installCapacity(): Unit =
    val source = "capacity"
    for
      origin <- FullUrl.build(baseUrl)
      layerSource <- StringLayerSource.build(source)
    yield
      val capacityUrl = origin / "cars" / "parkings" / "capacity"
      map.addSource(source, GeoJsonSource(capacityUrl))
      val layer = Layer(
        capacityLayerId,
        LayerType.Fill,
        layerSource,
        None,
        Option(FillPaint("red", Option(0.2d)))
      )
      map.putLayer(layer)
      log.info(s"Added capacity layer from '$capacityUrl'.")

  private def installParkingAreas(): Unit =
    val parkingsSource = "parkings"
    StringLayerSource
      .build(parkingsSource)
      .map: layerSource =>
        map.addSource(parkingsSource, GeoJsonSource(parkingsUrl))
        val layer = Layer(
          parkingsLayerId,
          LayerType.Fill,
          layerSource,
          None,
          Option(FillPaint("blue", Option(0.1)))
        )
        map.putLayer(layer)
        log.info(s"Added parkings from '$parkingsUrl'.")

  private def installHoverListener(): Unit =
    map.onHoverEnter(Seq(parkingsLayerId, capacityLayerId))(
      inEvent =>
        log.debug(s"Hover over ${JSON.stringify(inEvent.features)}")
        inEvent.features
          .as[Seq[Feature]]
          .map: fs =>
            val parkingProps = fs.map: f =>
              f.props.as[ParkingProps]
            val capacityProps = fs.map: f =>
              f.props.as[CapacityProps]
            parkingProps
              .flatMap(_.toOption)
              .filterNot(_.isEmpty)
              .take(1)
              .map: parking =>
                val capacity =
                  capacityProps.flatMap(_.toOption).flatMap(_.capacityEstimate).headOption
                log.info(s"Popup with $parking and $capacity")
                popup.show(
                  popupsHtml.parking(parking, capacity),
                  inEvent.lngLat,
                  map
                )
      ,
      _ =>
        log.info("Hovered out.")
        popup.remove()
    )
