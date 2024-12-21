package com.malliina.boat

import cats.effect.{Async, Resource}
import cats.implicits.{toFunctorOps, toFlatMapOps}
import com.malliina.boat.MapView.MapEvent
import com.malliina.datepicker.{TempusDominus, TimeLocale, TimeRestrictions, updateDate}
import com.malliina.http.Http
import com.malliina.mapbox.*
import com.malliina.values.{ErrorMessage, Readable}
import fs2.Stream
import fs2.concurrent.Topic
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.*

import scala.concurrent.duration.DurationInt
import scala.scalajs.js.{Date, JSON, URIUtils}

object MapView extends CookieNames:
  enum MapEvent:
    case Load(center: Coord)
    case MoveEnd(center: Coord)

  def default[F[_]: Async](
    messages: Topic[F, WebSocketEvent],
    http: Http[F]
  ): Resource[F, MapView[F]] =
    val result = readCookie[AccessToken](TokenCookieName).left
      .map(err => Exception(err.message))
    val task = for
      mapEvents <- Topic[F, MapEvent]
      token <- Async[F].fromEither(result)
    yield
      val lang = readCookie[Language](LanguageName).getOrElse(Language.default)
      MapView[F](token, lang, messages, mapEvents, http)
    Resource.eval(task)

  def readCookie[T](key: String)(using r: Readable[T]): Either[ErrorMessage, T] =
    cookies.get(key).toRight(ErrorMessage(s"Not found: '$key'.")).flatMap(c => r.read(c))

  private def cookies: Map[String, String] = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect:
      case key :: value :: _ => key -> value
    .toMap

class MapView[F[_]: Async](
  accessToken: AccessToken,
  language: Language,
  messages: Topic[F, WebSocketEvent],
  mapEvents: Topic[F, MapEvent],
  http: Http[F],
  val log: BaseLogger = BaseLogger.console
) extends BaseFront:
  val F = Async[F]
  mapboxGl.accessToken = accessToken.token
  val lang = Lang(language)

  private val initialSettings = MapCamera()
  private val mapOptions = MapOptions(
    container = MapId,
    style = MapConf.active.styleUrl,
    center = initialSettings.center,
    zoom = initialSettings.zoom,
    hash = true
  )
  val map = MapboxMap(mapOptions)
  val parking = Parking(map, lang)
  private val geocoder = MapboxGeocoder.finland(accessToken)
  val pathFinder = PathFinder(map, http)
  val settings = MapSettings
  private val SearchKey = "s"
  private val DirectionsKey = "d"
  private var isGeocoderVisible = false

  elemAsGet[HTMLDivElement](MapId).onkeypress = (e: KeyboardEvent) =>
    if !document.activeElement.isInstanceOf[HTMLInputElement] then
      e.key match
        case SearchKey =>
          if isGeocoderVisible then map.removeControl(geocoder)
          else
            map.addControl(geocoder)
            // focuses the search box when opened
            focusSearch("mapboxgl-ctrl-geocoder", e)
          isGeocoderVisible = !isGeocoderVisible
        case DirectionsKey =>
          pathFinder.toggleState()
        case _ =>
          ()

  def mode = if Option(href.getFragment).isDefined then MapMode.Stay else MapMode.Fit
  def sample = queryInt(SampleKey).getOrElse(1)
  val formsLang = lang.settings.forms
  val locale = language match
    case Language.swedish => TimeLocale.Sv
    case Language.finnish => TimeLocale.Fi
    case Language.english => TimeLocale.En
    case _                => TimeLocale.En
  val socket: MapSocket[F] =
    MapSocket(map, pathFinder, mode, messages, http.dispatcher, lang, log)
  private val events = socket.events.coordEvents
    .debounce(2.seconds)
    .tap: e =>
      socket.fitToMap()
  private val mapEventHandler = mapEvents
    .subscribe(10)
    .map:
      case MapEvent.Load(center) => center
      case MapEvent.MoveEnd(center) =>
        onMoveEnd(center)
        center
//    .changes
    .switchMap: center =>
      Stream.eval(fetchHistory(center))

  val runnables = events.concurrently(socket.task).concurrently(mapEventHandler)
  map.on(
    "load",
    () =>
      http.dispatcher.unsafeRunAndForget(mapEvents.publish1(MapEvent.Load(mapCenter)))
      reconnect()
      if initialSettings.customCenter then
        map.putLayer(
          Layer.symbol(
            "custom-center",
            pathFinder.pointFor(initialSettings.center),
            ImageLayout("border-dot-13", `icon-size` = 1)
          )
        )
  )

  map.on(
    "moveend",
    () =>
      http.dispatcher.unsafeRunAndForget(
        mapEvents.publish1(MapEvent.MoveEnd(mapCenter))
      )
  )

  private def mapCenter = LngLat.coord(map.getCenter())

  private def onMoveEnd(coord: Coord) =
    val camera = MapCamera(coord, map.getZoom(), false)
    settings.save(camera)
    fetchHistory(coord)

  private def fetchHistory(coord: Coord): F[Seq[Unit]] =
    val query =
      Map(
        FrontKeys.Lat -> s"${coord.lat}",
        FrontKeys.Lng -> s"${coord.lng}",
        FrontKeys.TracksLimit -> "3"
      )
    val qString = query.map((k, v) => s"$k=$v").mkString("&")
    http.client
      .get[Seq[CoordsEvent]](s"history?$qString")
      .map: coords =>
        log.info(s"Fetched history, got ${coords.size} tracks near $coord")
        coords.map: event =>
          socket.onCoordsHistory(event)

  elem(ModalId).foreach(initModal)

  initNavDropdown()

  private def reconnect(): Unit =
    socket.reconnect(parseUri, Option(sample))

  private val oneDayMs = 86400000L
  private val dateHandler = DateHandler()
  for
    fromElem <- elemAs[Element](FromTimePickerId)
    toElem <- elemAs[Element](ToTimePickerId)
    shortcutsElem <- elemAs[HTMLSelectElement](ShortcutsId)
  yield
    val tomorrow = new Date(Date.now() + oneDayMs)
    val fromPicker = makePicker(FromTimePickerId, maxDate = Option(tomorrow))
    val toPicker = makePicker(ToTimePickerId, None)
    val _ = dateHandler.subscribeDate(fromPicker, toPicker, isFrom = true, locale = locale): from =>
      datesChanged(from, dateHandler.to)
    val _ = dateHandler.subscribeDate(toPicker, fromPicker, isFrom = false, locale = locale): to =>
      datesChanged(dateHandler.from, to)
    shortcutsElem.onchange = e =>
      val value = shortcutsElem.value
      Shortcut
        .fromString(value)
        .foreach: shortcut =>
          dateHandler.onShortcut(shortcut)
          fromPicker.updateDate(dateHandler.from)
          toPicker.updateDate(dateHandler.to)
          datesChanged(dateHandler.from, dateHandler.to)
      TrackShortcut
        .fromString(value)
        .map: shortcut =>
          fromPicker.updateDate(None)
          toPicker.updateDate(None)
          QueryString.transact(
            TracksLimit -> Option(shortcut.latest),
            Timings.From -> None,
            Timings.To -> None
          )
          reconnect()

  private def datesChanged(from: Option[Date], to: Option[Date]): Unit =
    QueryString.transact(Timings.From -> from, Timings.To -> to, TracksLimit -> None)
    reconnect()

  private def makePicker(elementId: String, maxDate: Option[Date]): TempusDominus =
    TempusDominus(
      elemAsGet(elementId),
      DateHandler.timeOptions(TimeRestrictions(None, maxDate), locale)
    )

  private def focusSearch(className: String, e: KeyboardEvent) =
    document
      .getElementsByClassName(className)
      .map(
        _.getElementsByTagName("input")
          .map(_.asInstanceOf[HTMLInputElement])
          .headOption
          .map: in =>
            e.preventDefault()
            in.focus()
      )

  private def initModal(modal: Element): Unit =
    window.addOnClick: e =>
      if e.target == modal then modal.hide()
    modal
      .getElementsByClassName(Close)
      .headOption
      .foreach: node =>
        node.asInstanceOf[HTMLSpanElement].onclick = _ => modal.hide()
    elemAs[HTMLSpanElement](Question).foreach: q =>
      q.onclick = _ => modal.show()

  private def initNavDropdown(): Unit =
    initDropdown(DropdownLinkId, DropdownContentId)
    initDropdown(BoatDropdownId, BoatDropdownContentId)

  private def initDropdown(linkId: String, contentId: String): Unit =
    htmlElem(linkId).foreach: link =>
      htmlElem(contentId).foreach: content =>
        link.addOnClick(_ => toggleClass(content, Visible))
        window.addOnClick: e =>
          if e.target.isOutside(content) && e.target.isOutside(link) && content.classList.contains(
              Visible
            )
          then content.classList.remove(Visible)

  private def toggleClass(e: HTMLElement, className: String): Unit =
    val classList = e.classList
    if classList.contains(className) then classList.remove(className)
    else classList.add(className)

  private def htmlElem(id: String) = elemAs[HTMLElement](id)

  def parse[T: Encoder](t: T) = JSON.parse(t.asJson.noSpaces)
