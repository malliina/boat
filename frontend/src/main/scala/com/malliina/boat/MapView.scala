package com.malliina.boat

import com.malliina.mapbox.{MapOptions, MapboxMap, mapboxGl}
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, window}
import play.api.libs.json.{Json, Writes}

import scala.scalajs.js.{JSON, URIUtils}

object MapView {
  def apply(accessToken: AccessToken): MapView = new MapView(accessToken)

  def apply(): MapView = apply(AccessToken(cookies(Constants.TokenCookieName)))

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
    socket = Option(new MapSocket(map, readTrack, Option(sample), mode))
  })

  def readTrack = href.getPath.split('/').toList match {
    case _ :: "tracks" :: track :: _ => Option(TrackName(track))
    case _ => None
  }

  elem(ModalId).foreach(initModal)

  initNav()

  def craftSampleQuery = {
    val prefix = if (queryString.isEmpty) "" else "&"
    s"$prefix$SampleKey=${Constants.DefaultSample}"
  }

  def initModal(modal: Element): Unit = {
    def hideModal(): Unit = if (!modal.classList.contains(Hidden)) modal.classList.add(Hidden)

    window.addEventListener("click", (e: Event) => if (e.target == modal) hideModal())
    modal.getElementsByClassName(Close).headOption.foreach { node =>
      node.asInstanceOf[HTMLSpanElement].onclick = _ => hideModal()
    }
    elem(Question).foreach { e =>
      val q = e.asInstanceOf[HTMLSpanElement]
      q.onclick = _ => {
        modal.classList.remove(Hidden)
      }
    }
  }

  def initNav(): Unit = {
    elem(DropdownLinkId).map(_.asInstanceOf[HTMLElement]).foreach { e =>
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
