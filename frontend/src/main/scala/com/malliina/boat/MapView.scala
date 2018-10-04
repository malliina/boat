package com.malliina.boat

import java.net.URI

import com.malliina.boat.FrontKeys._
import com.malliina.mapbox.{MapOptions, MapboxMap, mapboxGl}
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, window}
import play.api.libs.json.{Json, Writes}

import scala.scalajs.js.{JSON, URIUtils}
import scala.util.Try

object MapView {
  def apply(accessToken: AccessToken): MapView = new MapView(accessToken)

  def apply(): MapView = apply(AccessToken(cookies(Constants.TokenCookieName)))

  def cookies = URIUtils.decodeURIComponent(document.cookie).split(";").toList
    .map(_.trim.split("=").toList)
    .collect { case key :: value :: _ => key -> value }
    .toMap
}

class MapView(accessToken: AccessToken) {
  mapboxGl.accessToken = accessToken.token
  val href = new URI(window.location.href)
  val queryString = Option(href.getQuery).getOrElse("")
  val queryParams = queryString.split("&").toList
    .map { kv => kv.split("=").toList }
    .collect { case key :: value :: Nil => key -> value }
    .groupBy { case (key, _) => key }
    .mapValues { vs => vs.map { case (_, v) => v } }
  val mapOptions = MapOptions(
    container = MapId,
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(lng = 24.9000, lat = 60.1400),
    zoom = 13,
    hash = true
  )
  val map = new MapboxMap(mapOptions)
  var socket: Option[MapSocket] = None

  map.on("load", () => {
    val mode = if (Option(href.getFragment).isDefined) Stay else Fit
    socket = Option(new MapSocket(map, queryString, mode))
  })

  initModal()
  initNav()

  def queryDouble(key: String) = query(key).flatMap(s => Try(s.toDouble).toOption)

  def query(key: String) = queryParams.get(key).flatMap(_.headOption)

  def initModal(): Unit = {
    val modal = elem(ModalId)

    def hideModal(): Unit = if (!modal.classList.contains(Hidden)) modal.classList.add(Hidden)

    window.addEventListener("click", (e: Event) => if (e.target == modal) hideModal())
    modal.getElementsByClassName(Close).headOption.foreach { node =>
      node.asInstanceOf[HTMLSpanElement].onclick = _ => hideModal()
    }
    val q = elem(Question).asInstanceOf[HTMLSpanElement]
    q.onclick = _ => {
      modal.classList.remove(Hidden)
    }
  }

  def initNav(): Unit = {
    Option(elem(DropdownLinkId)).map(_.asInstanceOf[HTMLElement]).foreach { e =>
      val content = htmlElem(DropdownContentId)
      e.addEventListener("click", (_: Event) => toggleClass(content, Visible))
      window.addEventListener("click", (e: Event) => {
        if (e.target == content) htmlElem(DropdownContentId).classList.remove(Visible)
      })
    }
  }

  def toggleClass(e: HTMLElement, className: String): Unit = {
    val classList = e.classList
    if (classList.contains(className)) classList.remove(className)
    else classList.add(className)
  }

  def htmlElem(id: String) = elem(id).asInstanceOf[HTMLElement]

  def elem(id: String) = document.getElementById(id)

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
