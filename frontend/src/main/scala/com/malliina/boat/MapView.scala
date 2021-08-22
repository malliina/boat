package com.malliina.boat

import com.malliina.mapbox.{LngLat, MapOptions, MapboxGeocoder, MapboxMap, mapboxGl}
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, window}
import io.circe._
import io.circe.syntax.EncoderOps

import scala.scalajs.js.{JSON, URIUtils}

object MapView extends CookieNames {
  def apply(): Either[NotFound, MapView] = {
    val lang = readCookie(LanguageName).map(Language.apply).getOrElse(Language.default)
    readCookie(TokenCookieName).map(t => apply(AccessToken(t), lang))
  }

  def apply(accessToken: AccessToken, language: Language): MapView =
    new MapView(accessToken, language)

  def readCookie(key: String): Either[NotFound, String] =
    cookies.get(key).toRight(NotFound(key))

  def cookies: Map[String, String] = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap
}

class MapView(
  accessToken: AccessToken,
  language: Language,
  val log: BaseLogger = BaseLogger.console
) extends BaseFront {
  mapboxGl.accessToken = accessToken.token

  val initialSettings = MapCamera()
  val mapOptions = MapOptions(
    container = MapId,
    style = MapConf.active.styleUrl,
    center = initialSettings.center,
    zoom = initialSettings.zoom,
    hash = true
  )
  val map = new MapboxMap(mapOptions)
  val geocoder = MapboxGeocoder.finland(accessToken)
  val pathFinder = PathFinder(map)
  val settings = MapSettings
  val SearchKey = "s"
  val DirectionsKey = "d"
  private var isGeocoderVisible = false

  elemAs[HTMLDivElement](MapId).toOption.get.onkeypress = (e: KeyboardEvent) => {
    if (!document.activeElement.isInstanceOf[HTMLInputElement]) {
      e.key match {
        case SearchKey =>
          if (isGeocoderVisible) {
            map.removeControl(geocoder)
          } else {
            map.addControl(geocoder)
            // focuses the search box when opened
            focusSearch("mapboxgl-ctrl-geocoder", e)
          }
          isGeocoderVisible = !isGeocoderVisible
        case DirectionsKey =>
          pathFinder.toggleState()
        case _ =>
          ()
      }
    }
  }
  var socket: Option[MapSocket] = None

  map.on(
    "load",
    () => {
      val mode = if (Option(href.getFragment).isDefined) MapMode.Stay else MapMode.Fit
      val sample = queryInt(SampleKey).getOrElse(Constants.DefaultSample)
      socket = Option(new MapSocket(map, pathFinder, readTrack, Option(sample), mode, language))
    }
  )

  map.on(
    "moveend",
    () => {
      val camera = MapCamera(LngLat.coord(map.getCenter()), map.getZoom())
      settings.save(camera)
    }
  )

  elem(ModalId).foreach(initModal)

  initNavDropdown()

  private def focusSearch(className: String, e: KeyboardEvent) = {
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
  }

  def craftSampleQuery = {
    val prefix = if (queryString.isEmpty) "" else "&"
    s"$prefix$SampleKey=${Constants.DefaultSample}"
  }

  def initModal(modal: Element): Unit = {
    window.addOnClick { e =>
      if (e.target == modal) {
        modal.hide()
      }
    }
    modal.getElementsByClassName(Close).headOption.foreach { node =>
      node.asInstanceOf[HTMLSpanElement].onclick = _ => modal.hide()
    }
    elemAs[HTMLSpanElement](Question).foreach { q =>
      q.onclick = _ => {
        modal.show()
      }
    }
  }

  def initNavDropdown(): Unit = {
    initDropdown(DropdownLinkId, DropdownContentId)
    initDropdown(BoatDropdownId, BoatDropdownContentId)
    initDeviceDropdown()
  }

  private def initDropdown(linkId: String, contentId: String): Unit =
    htmlElem(linkId).foreach { link =>
      htmlElem(contentId).foreach { content =>
        link.addOnClick(_ => toggleClass(content, Visible))
        window.addOnClick { e =>
          if (
            e.target.isOutside(content) && e.target.isOutside(link) && content.classList.contains(
              Visible
            )
          ) {
            content.classList.remove(Visible)
          }
        }
      }
    }

  private def initDeviceDropdown() = document.getElementsByClassName(DeviceLinkClass).map { boat =>
    boat.addOnClick { e =>
      val boatName = BoatName(e.target.asInstanceOf[HTMLElement].getAttribute("data-name"))
      socket.foreach { s =>
        s.fly(boatName)
      }
    }
  }

  def toggleClass(e: HTMLElement, className: String): Unit = {
    val classList = e.classList
    if (classList.contains(className)) classList.remove(className)
    else classList.add(className)
  }

  def htmlElem(id: String) = elemAs[HTMLElement](id)

  def parse[T: Encoder](t: T) = JSON.parse(t.asJson.noSpaces)
}
