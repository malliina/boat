package com.malliina.boat.html

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.boat.FrontKeys._
import com.malliina.boat.html.BoatHtml.callAttr
import com.malliina.boat.http.Limits
import com.malliina.boat.{FullTrack, TrackRef, UserBoats, Usernames}
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
    val opt = if (isProd) "opt" else "fastopt"
    new BoatHtml(Seq(s"$name-$opt-library.js", s"$name-$opt-loader.js", s"$name-$opt.js"))
  }
}

class BoatHtml(jsFiles: Seq[String]) extends Tags(scalatags.Text) {
  val reverse = routes.BoatController
  val reverseApp = routes.AppController
  val mapboxVersion = "0.52.0"

  implicit def wrapFrag[T <: Wrapped](w: T): StringFrag = stringFrag(w.value)

  implicit def wrapAttr[T <: Wrapped]: AttrValue[T] = {
    (t: Builder, a: Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(v.value))
  }

  def list(track: FullTrack, current: Limits) =
    page(PageConf(SentencesPage(track, current), bodyClasses = Seq(ListClass)))

  def chart(track: TrackRef) = page(Charts.chart(track))

  def map(ub: UserBoats) = {
    val lang = BoatLang(ub.language)
    val about = About(lang.web)
    val user = ub.user
    val isAnon = user == Usernames.anon
    val mapClass = if (ub.boats.isEmpty) "anon" else "auth"
    page(
      PageConf(
        modifier(
          ub.boats.headOption.map { b =>
            modifier(
              div(id := "navbar", `class` := "navbar navbar-boat")(
                span(`class` := "nav-text")(b.boat),
                div(`class` := "dropdown nav-text", id := DropdownLinkId)(
                  span(`class` := "dropdown-button", lang.lang.tracks),
                  div(`class` := "dropdown-content", id := DropdownContentId)(
                    b.tracks.map { t =>
                      a(`class` := "track-link", href := reverse.track(t.trackName))(
                        span(t.describe),
                        span(short(t.distance)),
                        span(t.startEndRange)
                      )
                    }
                  )
                ),
                span(id := TitleId, `class` := "nav-text title")(""),
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
            if (isAnon) {
              modifier(
                standaloneQuestion("boat-icon question"),
                personIcon("boat-icon person")
              )
            } else {
              standaloneQuestion("boat-icon question")
            }
          },
          div(id := MapId, `class` := s"mapbox-map $mapClass"),
          about.about(user, ub.language),
        ),
        bodyClasses = Seq(s"$MapClass $AboutClass"),
        cssLink(s"https://api.tiles.mapbox.com/mapbox-gl-js/v$mapboxVersion/mapbox-gl.css")
      )
    )
  }

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
