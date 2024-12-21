package com.malliina.boat

import com.malliina.boat.LayerType.Fill
import com.malliina.boat.parking.ParkingProps
import com.malliina.http.FullUrl
import com.malliina.mapbox.{MapboxMap, MapboxPopup, PopupOptions}
import org.scalajs.dom.window

import scala.scalajs.js.JSON

class Parking(map: MapboxMap, language: Lang, val log: BaseLogger = BaseLogger.console):
  val location = window.location
//  val parkingsUrl2: FullUrl = https"pubapi.parkkiopas.fi/public/v1/parking_area/?format=json"
//  val parkingsUrl: FullUrl =
//    FullUrl(location.protocol.stripSuffix(":"), location.host, "/assets/parking-areas.json")
  val parkingsUrl: FullUrl = FullUrl.https(
    "kartta.hel.fi",
    "/ws/geoserver/avoindata/wfs?request=getFeature&typeNames=avoindata:Pysakointipaikat_alue&outputFormat=application/json&srsName=EPSG:4326"
  )

  val popup = MapboxPopup(PopupOptions())
  val popupsHtml = Popups(language)
  map.on(
    "load",
    () =>
      val parkingsSource = "parkings"
      val str = Parsing.stringify(UrlSource("geojson", parkingsUrl))
      map.addSource(parkingsSource, JSON.parse(str))
      val layer = Layer(
        "parkings-layer",
        Fill,
        StringLayerSource(parkingsSource),
        None,
        Option(FillPaint("blue", Option(0.1)))
      )
      map.putLayer(layer)
      log.info(s"Added parkings from $parkingsUrl.")
      map.onHoverEnter(layer.id)(
        inEvent =>
          Parsing
            .asJson[Seq[Feature]](inEvent.features)
            .map: fs =>
              fs.map: f =>
                f.props
                  .as[ParkingProps]
                  .fold(
                    err => log.info(s"Failed to decode ${f.props}: $err."),
                    pp =>
                      log.debug(s"Hover on ${layer.id} with features $fs.")
                      popup.show(popupsHtml.parking(pp), inEvent.lngLat, map)
                  ),
        outEvent =>
          log.debug(s"Hovered out from ${layer.id}.")
          popup.remove()
      )
  )
