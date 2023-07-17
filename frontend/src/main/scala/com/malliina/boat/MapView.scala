package com.malliina.boat

import com.malliina.datepicker.{DateFormats, TempusDominus, TimeLocalization, TimeOptions, TimeRestrictions}
import com.malliina.mapbox.*
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.*

import scala.scalajs.js.{Date, JSON, URIUtils}

object MapView extends CookieNames:
  def default: Either[NotFound, MapView] =
    val lang = readCookie(LanguageName).map(Language.apply).getOrElse(Language.default)
    readCookie(TokenCookieName).map(t => MapView(AccessToken(t), lang))

  def readCookie(key: String): Either[NotFound, String] =
    cookies.get(key).toRight(NotFound(key))

  private def cookies: Map[String, String] = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap

class MapView(
  accessToken: AccessToken,
  language: Language,
  val log: BaseLogger = BaseLogger.console
) extends BaseFront:
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
  val pathFinder = PathFinder(map)
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
  val socket: MapSocket = MapSocket(map, pathFinder, mode, language, log)

  map.on(
    "load",
    () =>
      reconnect(from = None, to = None)
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

  private def reconnect(from: Option[Date], to: Option[Date]): Unit =
    socket.reconnect(parseUri, Option(sample), from, to)

  private val dateHandler = DateHandler(log)
  private val fromPicker = makePicker(FromTimePickerId)
  private val toPicker = makePicker(ToTimePickerId)

  private val _ = dateHandler.subscribeDate(fromPicker, toPicker, isFrom = true) { from =>
    reconnect(from, dateHandler.to)
  }
  private val _ = dateHandler.subscribeDate(toPicker, fromPicker, isFrom = false) { to =>
    reconnect(dateHandler.from, to)
  }

  private def makePicker(elementId: String): TempusDominus =
    TempusDominus(
      elemGet(elementId),
      TimeOptions(TimeRestrictions(None, None), TimeLocalization(DateFormats.default))
    )

  private def focusSearch(className: String, e: KeyboardEvent) =
    document
      .getElementsByClassName(className)
      .map(
        _.getElementsByTagName("input")
          .map(_.asInstanceOf[HTMLInputElement])
          .headOption
          .map { in =>
            e.preventDefault()
            in.focus()
          }
      )

  private def initModal(modal: Element): Unit =
    window.addOnClick { e =>
      if e.target == modal then modal.hide()
    }
    modal.getElementsByClassName(Close).headOption.foreach { node =>
      node.asInstanceOf[HTMLSpanElement].onclick = _ => modal.hide()
    }
    elemAs[HTMLSpanElement](Question).foreach { q =>
      q.onclick = _ => modal.show()
    }

  private def initNavDropdown(): Unit =
    initDropdown(DropdownLinkId, DropdownContentId)
    initDropdown(BoatDropdownId, BoatDropdownContentId)

  private def initDropdown(linkId: String, contentId: String): Unit =
    htmlElem(linkId).foreach { link =>
      htmlElem(contentId).foreach { content =>
        link.addOnClick(_ => toggleClass(content, Visible))
        window.addOnClick { e =>
          if e.target.isOutside(content) && e.target.isOutside(link) && content.classList.contains(
              Visible
            )
          then content.classList.remove(Visible)
        }
      }
    }

  private def toggleClass(e: HTMLElement, className: String): Unit =
    val classList = e.classList
    if classList.contains(className) then classList.remove(className)
    else classList.add(className)

  private def htmlElem(id: String) = elemAs[HTMLElement](id)

  def parse[T: Encoder](t: T) = JSON.parse(t.asJson.noSpaces)
