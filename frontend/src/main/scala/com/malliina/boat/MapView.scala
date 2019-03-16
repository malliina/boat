package com.malliina.boat

import com.malliina.mapbox.{MapOptions, MapboxGeocoder, MapboxMap, mapboxGl}
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, window}
import play.api.libs.json.{Json, Writes}

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

  def cookies = URIUtils
    .decodeURIComponent(document.cookie)
    .split(";")
    .toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap
}

class MapView(accessToken: AccessToken,
              language: Language,
              val log: BaseLogger = BaseLogger.console)
    extends BaseFront {
  mapboxGl.accessToken = accessToken.token
  val mapOptions = MapOptions(
    container = MapId,
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(lng = Longitude(24.9), lat = Latitude(60.14)),
    zoom = 13,
    hash = true
  )
  val map = new MapboxMap(mapOptions)
  val geocoder = MapboxGeocoder.finland(accessToken)
  var isCoderVisible = false

  elemAs[HTMLDivElement](MapId).right.get.onkeypress = (e: KeyboardEvent) => {
    if (e.key == "s" && !document.activeElement.isInstanceOf[HTMLInputElement]) {
      if (isCoderVisible) map.removeControl(geocoder)
      else map.addControl(geocoder)
      isCoderVisible = !isCoderVisible
    }
  }
  var socket: Option[MapSocket] = None

  map.on(
    "load",
    () => {
      val mode = if (Option(href.getFragment).isDefined) MapMode.Stay else MapMode.Fit
      val sample = queryInt(SampleKey).getOrElse(Constants.DefaultSample)
      socket = Option(new MapSocket(map, readTrack.toOption, Option(sample), mode, language))
    }
  )

  elem(ModalId).foreach(initModal)

  initNavDropdown()

  def craftSampleQuery = {
    val prefix = if (queryString.isEmpty) "" else "&"
    s"$prefix$SampleKey=${Constants.DefaultSample}"
  }

  def initModal(modal: Element): Unit = {
    window.addOnClick(e => if (e.target == modal) modal.hide())
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
    htmlElem(DropdownLinkId).foreach { e =>
      htmlElem(DropdownContentId).foreach { content =>
        e.addOnClick(_ => toggleClass(content, Visible))
        window.addOnClick { e =>
          if (e.target == content) htmlElem(DropdownContentId).foreach(_.classList.remove(Visible))
        }
      }
    }
  }

  def toggleClass(e: HTMLElement, className: String): Unit = {
    val classList = e.classList
    if (classList.contains(className)) classList.remove(className)
    else classList.add(className)
  }

  def htmlElem(id: String) = elemAs[HTMLElement](id)

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
