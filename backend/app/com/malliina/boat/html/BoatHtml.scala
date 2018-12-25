package com.malliina.boat.html

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.boat.FrontKeys._
import com.malliina.boat.docs.Docs
import com.malliina.boat.html.BoatHtml.callAttr
import com.malliina.boat.http.Limits
import com.malliina.boat.{BoatInfo, FullTrack, TrackRef}
import com.malliina.html.Tags
import com.malliina.measure.Distance
import com.malliina.play.tags.TagPage
import com.malliina.values.Wrapped
import controllers.routes
import play.api.Mode
import play.api.http.MimeTypes
import play.api.mvc.Call
import scalatags.Text.GenericAttr
import scalatags.Text.all._
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatHtml {
  implicit val callAttr = new GenericAttr[Call]

  def apply(mode: Mode): BoatHtml = apply(mode == Mode.Prod)

  def apply(isProd: Boolean): BoatHtml = {
    val name = "frontend"
    val jsFiles =
      if (isProd) Seq(s"$name-opt-library.js", s"$name-opt-loader.js", s"$name-opt.js")
      else Seq(s"$name-fastopt-library.js", s"$name-fastopt-loader.js", s"$name-fastopt.js")
    new BoatHtml(jsFiles)
  }
}

class BoatHtml(jsFiles: Seq[String]) extends Tags(scalatags.Text) {
  val defer = attr("defer").empty
  val reverse = routes.BoatController
  val reverseApp = routes.AppController
  val mapboxVersion = "0.52.0"

  implicit def wrapFrag[T <: Wrapped](w: T): StringFrag = stringFrag(w.value)

  implicit def wrapAttr[T <: Wrapped]: AttrValue[T] = {
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(v.value))
  }

  def list(track: FullTrack, current: Limits) = page(PageConf(TrackList(track, current)))

  def chart(track: TrackRef) = page(Charts.chart(track))

  def docs: TagPage = markdownPage(Docs.agent)

  def support: TagPage = markdownPage(Docs.support)

  def privacyPolicy: TagPage = markdownPage(Docs.privacyPolicy)

  private def markdownPage(md: RawFrag) = page(PageConf(md, bodyClasses = Seq("docs-agent")))

  def map(boat: Option[BoatInfo]) = page(
    PageConf(
      modifier(
        boat.map { b =>
          modifier(
            div(id := "navbar", `class` := "navbar navbar-boat")(
              span(`class` := "nav-text")(b.boat),
              div(`class` := "dropdown nav-text", id := DropdownLinkId)(
                span(`class` := "dropdown-button", "Tracks"),
                div(`class` := "dropdown-content", id := DropdownContentId)(
                  b.tracks.map { t =>
                    a(`class` := "track-link", href := reverse.track(t.trackName))(
                      span(t.trackName),
                      span(short(t.distance)),
                      span(t.startEndRange)
                    )
                  }
                )
              ),
              span(id := DistanceId, `class` := "nav-text distance")(""),
              span(id := DurationId, `class` := "nav-text duration")(""),
              span(id := TopSpeedId, `class` := "nav-text top-speed")(""),
              span(id := WaterTempId, `class` := "nav-text water-temp")(""),
              iconLink(a, FullLinkId, s"icon-link $Hidden", "list", "List"),
              iconLink(a, GraphLinkId, s"icon-link $Hidden", "graph", "Graph"),
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
        Modal.about,
      ),
      bodyClasses = Seq(MapClass),
      cssLink(s"https://api.tiles.mapbox.com/mapbox-gl-js/v$mapboxVersion/mapbox-gl.css")
//      modifier(
//        jsScript(s"https://api.tiles.mapbox.com/mapbox-gl-js/v$mapboxVersion/mapbox-gl.js"),
//        jsScript("https://npmcdn.com/@turf/turf@5.1.6/turf.min.js")
//      )
    )
  )

  def short(d: Distance) =
    if (d.toKilometers >= 10) s"${d.toKilometers} km"
    else if (d.toMeters >= 10) s"${d.toMeters} m"
    else s"${d.toMillis} mm"

  def urlEncode(w: Wrapped): String = URLEncoder.encode(w.value, StandardCharsets.UTF_8.name())

  def standaloneQuestion(cls: String) =
    iconLink(span, Question, cls, "question-mark", "About")

  def personIcon(cls: String) =
    iconLink(a, PersonLink, cls, "person", "Sign in", href := routes.Social.google().toString)

  def iconLink(tag: ConcreteHtmlTag[String],
               idValue: String,
               cls: String,
               dataGlyph: String,
               titleValue: String,
               more: AttrPair*) =
    tag(id := idValue,
      `class` := s"oi $cls",
      data("glyph") := dataGlyph,
      title := titleValue,
      aria.hidden := "true", more)

  def page(content: PageConf) = TagPage(
    html(
      head(
        meta(charset := "utf-8"),
        titleTag("Boat Tracker"),
        deviceWidthViewport,
        link(rel := "icon", `type` := "image/png", href := "/assets/img/favicon.png"),
        cssLinkHashed(
          "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css",
          "sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"),
        cssLinkHashed(
          "https://use.fontawesome.com/releases/v5.3.1/css/all.css",
          "sha384-mzrmE5qonljUremFsqc01SB46JvROS7bZs3IO2EmfFsd15uHvIt+Y8vEf7N7fWAU"),
        cssLink(reverseApp.versioned("css/fonts.css")),
        cssLink(reverseApp.versioned("css/main.css")),
        content.css,
        content.js,
        jsFiles.map { jsFile =>
          script(`type` := MimeTypes.JAVASCRIPT, defer, src := reverseApp.versioned(jsFile))
        }
      ),
      body(`class` := content.bodyClasses.mkString(" "))(
        content.content
      )
    )
  )
}

case class PageConf(content: Modifier,
                    bodyClasses: Seq[String] = Nil,
                    css: Modifier = PageConf.empty,
                    js: Modifier = PageConf.empty)

object PageConf {
  val empty: Modifier = ""
}
