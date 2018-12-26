package com.malliina.boat

import com.malliina.mapbox.{MapOptions, MapboxMap, mapboxGl}
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, window}
import play.api.libs.json.{Json, Writes}

import scala.scalajs.js.{JSON, URIUtils}

object MapView {
  def apply(): Either[NotFound, MapView] =
    readCookie(Constants.TokenCookieName).map(t => apply(AccessToken(t)))

  def apply(accessToken: AccessToken): MapView = new MapView(accessToken)

  def readCookie(key: String): Either[NotFound, String] =
    cookies.get(key).toRight(NotFound(key))

  def cookies = URIUtils.decodeURIComponent(document.cookie).split(";").toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap
}

class MapView(accessToken: AccessToken) extends BaseFront {
  mapboxGl.accessToken = accessToken.token
  val mapOptions = MapOptions(
    container = MapId,
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(lng = 24.9, lat = 60.14),
    zoom = 13,
    hash = true
  )
  val map = new MapboxMap(mapOptions)
  var socket: Option[MapSocket] = None

  map.on("load", () => {
    val mode = if (Option(href.getFragment).isDefined) Stay else Fit
    val sample = queryInt(SampleKey).getOrElse(Constants.DefaultSample)
    socket = Option(new MapSocket(map, readTrack.toOption, Option(sample), mode))
  })

  elem(ModalId).foreach(initModal)

  initNavDropdown()

  def craftSampleQuery = {
    val prefix = if (queryString.isEmpty) "" else "&"
    s"$prefix$SampleKey=${Constants.DefaultSample}"
  }

  def initModal(modal: Element): Unit = {
    window.addEventListener("click", (e: Event) => if (e.target == modal) modal.hide())
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
        e.addEventListener("click", (_: Event) => toggleClass(content, Visible))
        window.addEventListener("click", (e: Event) => {
          if (e.target == content) htmlElem(DropdownContentId).foreach(_.classList.remove(Visible))
        })
      }
    }
  }

  def toggleClass(e: HTMLElement, className: String): Unit = {
    val classList = e.classList
    if (classList.contains(className)) classList.remove(className)
    else classList.add(className)
  }

  def htmlElem(id: String) = elem(id).map(_.asInstanceOf[HTMLElement])

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
