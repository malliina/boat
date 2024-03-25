package com.malliina.boat

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import com.malliina.boat.TrackShortcut.Latest
import com.malliina.datepicker.{TempusDominus, TimeLocale, TimeRestrictions}
import com.malliina.http.HttpClient
import com.malliina.mapbox.*
import fs2.concurrent.Topic
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.*

import scala.concurrent.duration.DurationInt
import scala.scalajs.js.{Date, JSON, URIUtils}

object MapView extends CookieNames:
  def default[F[_]: Async](
    messages: Topic[F, MessageEvent],
    dispatcher: Dispatcher[F],
    http: HttpClient[F]
  ): Resource[F, MapView[F]] =
    val F = Async[F]
    for token <- Resource.eval(
        readCookie(TokenCookieName).fold(
          nf => F.raiseError(Exception(s"Not found: '${nf.id}'.")),
          t => F.pure(t)
        )
      )
    yield
      val lang = readCookie(LanguageName).map(Language.apply).getOrElse(Language.default)
      MapView[F](AccessToken(token), lang, messages, dispatcher, http)

  def readCookie(key: String): Either[NotFound, String] =
    cookies.get(key).toRight(NotFound(key))

  private def cookies: Map[String, String] = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap

class MapView[F[_]: Async](
  accessToken: AccessToken,
  language: Language,
  messages: Topic[F, MessageEvent],
  dispatcher: Dispatcher[F],
  http: HttpClient[F],
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
  private val geocoder = MapboxGeocoder.finland(accessToken)
  val pathFinder = PathFinder(map, http)
  val settings = MapSettings
  private val SearchKey = "s"
  private val DirectionsKey = "d"
  private var isGeocoderVisible = false

  elemAs[HTMLDivElement](MapId).toOption.get.onkeypress = (e: KeyboardEvent) =>
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
  val lang = Lang(language)
  val formsLang = lang.settings.forms
  val locale = language match
    case Language.swedish => TimeLocale.Sv
    case Language.finnish => TimeLocale.Fi
    case Language.english => TimeLocale.En
    case _                => TimeLocale.En
  val socket: MapSocket[F] =
    MapSocket(map, pathFinder, mode, messages, dispatcher, lang, log)
  val events = socket.events.coordEvents
    .debounce(2.seconds)
    .tap: e =>
      socket.fitToMap()
  val runnables = events.concurrently(socket.task)
  map.on(
    "load",
    () =>
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
      val camera = MapCamera(LngLat.coord(map.getCenter()), map.getZoom(), false)
      settings.save(camera)
  )

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
          datesChanged(dateHandler.from, dateHandler.to)
      TrackShortcut
        .fromString(value)
        .map: shortcut =>
          val limit = shortcut match
            case TrackShortcut.Latest  => 1
            case TrackShortcut.Latest5 => 5
          val qs = QueryString.parse
          qs.set(TracksLimit, limit)
          qs.commit()
          reconnect()

  private def datesChanged(from: Option[Date], to: Option[Date]): Unit =
    val qs = QueryString.parse
    Seq(Timings.From -> from, Timings.To -> to).foreach: (k, date) =>
      date
        .map: d =>
          qs.set(k, d.toISOString())
        .getOrElse:
          qs.delete(k)
    qs.commit()
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
