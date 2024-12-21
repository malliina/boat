package com.malliina.boat

import cats.effect.Async
import cats.implicits.{toFlatMapOps, toFunctorOps}
import com.malliina.boat.LayerType.Fill
import com.malliina.boat.MapView.{MapEvent, readCookie}
import com.malliina.boat.parking.ParkingProps
import com.malliina.http.{FullUrl, Http}
import com.malliina.mapbox.{MapOptions, MapboxMap, MapboxPopup, PopupOptions, mapboxGl}
import fs2.concurrent.Topic
import org.scalajs.dom.window
import scalatags.JsDom.all.*

import scala.scalajs.js.JSON

object ParkingView extends CookieNames:
  def default[F[_]: Async](
    messages: Topic[F, WebSocketEvent],
    http: Http[F]
  ): F[ParkingView[F]] =
    val result = readCookie[AccessToken](TokenCookieName).left
      .map(err => Exception(err.message))
    for
      mapEvents <- Topic[F, MapEvent]
      token <- Async[F].fromEither(result)
    yield
      val lang = Lang(readCookie[Language](LanguageName).getOrElse(Language.default))
      ParkingView[F](token, lang, messages, mapEvents, http)

class ParkingView[F[_]: Async](
  accessToken: AccessToken,
  language: Lang,
  messages: Topic[F, WebSocketEvent],
  mapEvents: Topic[F, MapEvent],
  http: Http[F],
  val log: BaseLogger = BaseLogger.console
) extends BaseFront:
  val F = Async[F]
  mapboxGl.accessToken = accessToken.token

  private val initialSettings = MapCamera()
  private val mapOptions = MapOptions(
    container = MapId,
    style = MapConf.active.styleUrl,
    center = initialSettings.center,
    zoom = initialSettings.zoom,
    hash = true
  )
  val map = MapboxMap(mapOptions)

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

  val runnables = fs2.Stream.emit[F, Int](42)
