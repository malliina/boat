package com.malliina.boat.parking

import com.malliina.boat.LayerType.Fill
import com.malliina.boat.{BaseLogger, Feature, FillPaint, Lang, Layer, Popups, StringLayerSource}
import com.malliina.http.FullUrl
import com.malliina.http.UrlSyntax.https
import com.malliina.json.Parsing.as
import com.malliina.mapbox.{GeoJsonSource, MapboxMap, MapboxPopup, PopupOptions}
import org.scalajs.dom.window

class Parking(map: MapboxMap, language: Lang, val log: BaseLogger = BaseLogger.console):
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

  val capacityLayerId = "capacity-layer"
  val parkingsLayerId = "parkings-layer"

  private def installCapacity(): Unit =
    val source = "capacity"
    FullUrl
      .build(location.origin)
      .map: origin =>
        val capacityUrl = origin / "cars" / "parkings" / "capacity"
        map.addSource(source, GeoJsonSource(capacityUrl))
        val layer = Layer(
          capacityLayerId,
          Fill,
          StringLayerSource(source),
          None,
          Option(FillPaint("red", Option(0.2d)))
        )
        map.putLayer(layer)
        log.info(s"Added capacity layer from '$capacityUrl'.")

  private def installParkingAreas(): Unit =
    val parkingsSource = "parkings"
    map.addSource(parkingsSource, GeoJsonSource(parkingsUrl))
    val layer = Layer(
      parkingsLayerId,
      Fill,
      StringLayerSource(parkingsSource),
      None,
      Option(FillPaint("blue", Option(0.1)))
    )
    map.putLayer(layer)
    log.info(s"Added parkings from '$parkingsUrl'.")

  private def installHoverListener(): Unit =
    map.onHoverEnter(Seq(parkingsLayerId, capacityLayerId))(
      inEvent =>
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
      outEvent =>
        log.info(s"Hovered out.")
        popup.remove()
    )
