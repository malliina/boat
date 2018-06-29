package com.malliina.boat.html

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.boat.BoatInfo
import com.malliina.boat.FrontKeys._
import com.malliina.boat.html.BoatHtml.callAttr
import com.malliina.html.Tags
import com.malliina.play.tags.TagPage
import com.malliina.values.Wrapped
import controllers.routes
import play.api.Mode
import play.api.http.MimeTypes
import play.api.mvc.Call
import scalatags.Text.GenericAttr
import scalatags.Text.all._
import scalatags.text.Builder

object BoatHtml {
  implicit val callAttr = new GenericAttr[Call]

  def apply(mode: Mode): BoatHtml = apply(mode == Mode.Prod)

  def apply(isProd: Boolean): BoatHtml = {
    val jsFile = if (isProd) "frontend-opt.js" else "frontend-fastopt.js"
    new BoatHtml(jsFile)
  }
}

class BoatHtml(jsFile: String) extends Tags(scalatags.Text) {
  val defer = attr("defer").empty
  val reverse = routes.BoatController

  implicit def wrapFrag[T <: Wrapped](w: T): StringFrag = stringFrag(w.value)

  implicit def wrapAttr[T <: Wrapped]: AttrValue[T] = {
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(v.value))
  }

  def index(msg: String) = page(PageConf(h1(msg)))

  def map(boat: Option[BoatInfo]) = page(
    PageConf(
      modifier(
        boat.map { b =>
          modifier(
            div(id := "navbar", `class` := "navbar")(
              span(`class` := "nav-text")(b.boat),
              div(`class` := "dropdown nav-text", id := DropdownLinkId)(
                span(`class` := "dropdown-button", "Tracks"),
                div(`class` := "dropdown-content", id := DropdownContentId)(
                  b.tracks.map { t =>
                    a(`class` := "track-link", href := routes.BoatController.index().url + s"?track=${urlEncode(t.trackName)}")(
                      span(t.trackName),
                      span(t.distance.short),
                      span(t.startEndRange)
                    )
                  }
                )
              ),
              span(id := Distance, `class` := "nav-text distance")(""),
              standaloneQuestion("question-nav nav-icon")
            )
          )
        }.getOrElse {
          modifier(
            standaloneQuestion("boat-icon question"),
            personIcon("boat-icon person")
          )
        },
        div(id := MapId, `class` := boat.fold("mapbox-map anon")(_ => "mapbox-map auth")),
        about,
      ),
      bodyClasses = Seq(MapClass),
      cssLink("https://api.tiles.mapbox.com/mapbox-gl-js/v0.44.2/mapbox-gl.css"),
      modifier(
        jsScript("https://api.tiles.mapbox.com/mapbox-gl-js/v0.44.2/mapbox-gl.js"),
        jsScript("https://npmcdn.com/@turf/turf/turf.min.js")
      )
    )
  )

  def urlEncode(w: Wrapped): String = URLEncoder.encode(w.value, StandardCharsets.UTF_8.name())

  def standaloneQuestion(cls: String) =
    iconLink(span, Question, cls, "question-mark", "About")

  def personIcon(cls: String) =
    iconLink(a, PersonLink, cls, "person", "Sign in", href := routes.Social.google().toString)

  def iconLink(tag: ConcreteHtmlTag[String], idValue: String, cls: String, dataGlyph: String, titleValue: String, more: AttrPair*) =
    tag(id := idValue, `class` := s"oi $cls", data("glyph") := dataGlyph, title := titleValue, aria.hidden := "true", more)

  def about = div(id := ModalId, `class` := s"$Modal $Hidden")(
    div(`class` := "modal-content")(
      span(`class` := "close")(raw("&times;")),
      h2("Merikartta-aineistot"),
      p(a(href := "https://creativecommons.org/licenses/by/4.0/")("CC 4.0"), " ", "Lähde: Liikennevirasto. Ei navigointikäyttöön. Ei täytä virallisen merikartan vaatimuksia."),
      h2("Java Marine API"),
      p(a(href := "http://www.gnu.org/licenses/lgpl-3.0-standalone.html")("GNU LGPL"), " ", a(href := "https://ktuukkan.github.io/marine-api/")("https://ktuukkan.github.io/marine-api/")),
      h2("Open Iconic"),
      p("Open Iconic — ", a(href := "https://www.useiconic.com/open")("www.useiconic.com/open")),
      h2("Inspiration"),
      p("Inspired by ", a(href := "https://github.com/iaue/poiju.io")("POIJU.IO"), ".")
    )
  )

  def page(content: PageConf) = TagPage(
    html(
      head(
        meta(charset := "utf-8"),
        titleTag("Boat Tracker"),
        cssLink(reverse.versioned("css/main.css")),
        cssLink("https://fonts.googleapis.com/css?family=Roboto:100,300,400,500,700"),
        content.css,
        content.js,
        script(`type` := MimeTypes.JAVASCRIPT, defer, src := reverse.versioned(jsFile))
      ),
      body(`class` := content.bodyClasses.mkString(" "))(
        content.content
      )
    )
  )
}

case class PageConf(content: Modifier, bodyClasses: Seq[String] = Nil, css: Modifier = PageConf.empty, js: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
