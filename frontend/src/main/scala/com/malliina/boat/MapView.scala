package com.malliina.boat

import com.malliina.boat.FrontKeys._
import org.scalajs.dom.raw.HTMLSpanElement
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

class MapView(accessToken: AccessToken) {
  mapboxGl.accessToken = accessToken.token

  val mapOptions = MapOptions(
    container = MapId,
    style = "mapbox://styles/malliina/cjgny1fjc008p2so90sbz8nbv",
    center = Coord(24.9000, 60.1400),
    zoom = 13
  )
  val map = new MapboxMap(mapOptions)
  var socket: Option[MapSocket] = None

  map.on("load", () => {
    socket = Option(new MapSocket(map))
  })

  initModal()

  def initModal(): Unit = {
    val modal = document.getElementById(ModalId)

    def hideModal(): Unit = if (!modal.classList.contains(Hidden)) modal.classList.add(Hidden)

    window.onclick = e => {
      if (e.target == modal) hideModal()
    }
    modal.getElementsByClassName(Close).headOption.foreach { node =>
      node.asInstanceOf[HTMLSpanElement].onclick = _ => hideModal()
    }
    val q = elem(Question).asInstanceOf[HTMLSpanElement]
    q.onclick = _ => {
      modal.classList.remove(Hidden)
    }
  }

  def elem(id: String) = document.getElementById(id)

  def parse[T: Writes](t: T) = JSON.parse(Json.stringify(Json.toJson(t)))
}
