package com.malliina.boat.html

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.malliina.boat.FrontKeys._
import com.malliina.boat.html.BoatHtml.{ScriptAssets, callAttr}
import com.malliina.boat.http.{Limits, TrackQuery}
import com.malliina.boat.{AppConf, FullTrack, Lang, TrackRef, UserBoats, Usernames}
import com.malliina.html.Tags
import com.malliina.measure.Distance
import com.malliina.play.tags.TagPage
import com.malliina.values.Wrapped
import controllers.routes
import play.api.Mode
import play.api.http.MimeTypes
import play.api.mvc.Call
import scalatags.Text.all._
import scalatags.text.Builder
import scalatags.Text.GenericAttr

import scala.language.implicitConversions

object BoatHtml {
  implicit val callAttr: GenericAttr[Call] = new GenericAttr[Call]

  def apply(mode: Mode): BoatHtml = apply(mode == Mode.Prod)

  def apply(isProd: Boolean): BoatHtml = {
    val name = "frontend"
    val opt = if (isProd) "opt" else "fastopt"
    new BoatHtml(ScriptAssets(s"$name-$opt-library.js", s"$name-$opt-loader.js", s"$name-$opt.js"))
  }

  case class ScriptAssets(library: String, loader: String, app: String)
}

class BoatHtml(jsFiles: ScriptAssets) extends Tags(scalatags.Text) {
  val reverse = routes.BoatController
  val reverseApp = routes.AppController
//  val mapboxVersion = AppMeta.default.mapboxVersion

  implicit def wrapFrag[T <: Wrapped](w: T): StringFrag = stringFrag(w.value)
  implicit def wrapAttr[T <: Wrapped]: AttrValue[T] = boatStringAttr(_.value)

  def boatStringAttr[T](stringify: T => String): AttrValue[T] = { (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(stringify(v)))
  }

  def tracks(ts: Seq[TrackRef], query: TrackQuery, lang: Lang) =
    page(PageConf(TracksPage(ts, query, lang), bodyClasses = Nil))

  def list(track: FullTrack, current: Limits, lang: BoatLang) =
    page(PageConf(SentencesPage(track, current, lang), bodyClasses = Seq(FormsClass)))

  def chart(track: TrackRef, lang: BoatLang) =
    page(Charts.chart(track, lang))

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
              div(id := "navbar", `class` := "navbar navbar-boat py-1")(
                span(`class` := "nav-text")(b.boat),
                a(href := reverse.tracks(),
                  `class` := "icon-link history",
                  title := lang.lang.track.tracks),
                div(`class` := "dropdown nav-text tracks", id := DropdownLinkId)(
                  span(`class` := "dropdown-button", lang.lang.track.tracks),
                  div(`class` := "dropdown-content", id := DropdownContentId)(
                    b.tracks.map { t =>
                      a(`class` := "track-link", href := reverse.canonical(t.canonical))(
                        span(t.describe),
                        span(short(t.distance)),
                        span(t.times.range)
                      )
                    }
                  )
                ),
                span(id := TitleId, `class` := "nav-text title")(""),
                span(id := DistanceId, `class` := "nav-text distance")(""),
                span(id := DurationId, `class` := "nav-text duration")(""),
                span(id := TopSpeedId, `class` := "nav-text top-speed")(""),
                span(id := WaterTempId, `class` := "nav-text water-temp")(""),
                fontAwesomeLink(a, FullLinkId, "list", Hidden, "List"),
                fontAwesomeLink(a, GraphLinkId, "chart-area", Hidden, "Graph"),
                standaloneQuestion("question-nav")
              ),
              routeContainer
            )
          }.getOrElse {
            modifier(
              routeContainer,
              if (isAnon) {
                modifier(
                  standaloneQuestion("boat-icon framed question"),
                  personIcon("boat-icon framed user")
                )
              } else {
                standaloneQuestion("boat-icon question")
              }
            )
          },
          div(id := MapId, `class` := s"mapbox-map $mapClass"),
          about.about(user, ub.language),
        ),
        bodyClasses = Seq(s"$MapClass $AboutClass")
      )
    )
  }

  def routeContainer = div(id := RoutesContainer, `class` := RoutesContainer)(
    span(id := RouteLength, `class` := "nav-text route-length")(""),
    span(id := RouteText, `class` := "nav-text route-text")("")
  )

  def short(d: Distance) =
    if (d.toKilometers >= 10) s"${d.toKilometers} km"
    else if (d.toMeters >= 10) s"${d.toMeters} m"
    else s"${d.toMillis} mm"

  def urlEncode(w: Wrapped): String = URLEncoder.encode(w.value, StandardCharsets.UTF_8.name())

  def standaloneQuestion(cls: String) =
    fontAwesomeLink(span, Question, "question", cls, "About")

  def personIcon(cls: String) =
    fontAwesomeLink(a, PersonLink, "user", cls, "Sign in", href := routes.Social.google().toString)

  def fontAwesomeLink(tag: ConcreteHtmlTag[String],
                      idValue: String,
                      faIcon: String,
                      classes: String,
                      titleValue: String,
                      more: AttrPair*) =
    tag(id := idValue,
        `class` := s"icon-link $faIcon $classes",
        title := titleValue,
        aria.hidden := "true",
        more)

  def page(pageConf: PageConf) = TagPage(
    html(
      head(
        meta(charset := "utf-8"),
        meta(name := "description",
             content := "Free nautical charts for Finland with live AIS tracking."),
        meta(name := "keywords",
             content := "charts, nautical, boat, tracking, ais, live, vessels, marine"),
        titleTag(s"${AppConf.Name} - Free nautical charts for Finland"),
        deviceWidthViewport,
        StructuredData.appStructuredData,
        StructuredData.appLinkMetadata,
        link(rel := "icon", `type` := "image/png", href := "/assets/img/favicon.png"),
        Seq("vendors.css", "fonts.css", "styles.css").map { file =>
          cssLink(versioned(file))
        }
      ),
      body(`class` := pageConf.bodyClasses.mkString(" "))(
        pageConf.content,
        Seq(jsFiles.library, jsFiles.loader, jsFiles.app).map { jsFile =>
          script(`type` := MimeTypes.JAVASCRIPT, src := versioned(jsFile))
        }
      )
    )
  )

  def versioned(file: String) = reverseApp.versioned(file)
}
