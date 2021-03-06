package com.malliina.boat.html

import com.malliina.boat.FrontKeys._
import com.malliina.boat.html.BoatHtml.ScriptAssets
import com.malliina.boat.http.{Limits, TrackQuery}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{AppConf, AppMode, FullTrack, Lang, TrackRef, TracksBundle, UserBoats, UserInfo, Usernames}
import com.malliina.html.HtmlTags.{cssLink, deviceWidthViewport, titleTag}
import com.malliina.html.{Bootstrap, HtmlTags, TagPage}
import com.malliina.measure.DistanceM
import com.malliina.values.WrappedString
import org.http4s.Uri
import scalatags.Text.all._

import scala.language.implicitConversions

object BoatHtml {
  def fromBuild = apply(AppMode.fromBuild)

  def apply(mode: AppMode): BoatHtml = apply(mode == AppMode.Prod)

  def apply(isProd: Boolean): BoatHtml = {
    val name = "frontend"
    val opt = if (isProd) "opt" else "fastopt"
    new BoatHtml(ScriptAssets(s"$name-$opt-library.js", s"$name-$opt-loader.js", s"$name-$opt.js"))
  }

  case class ScriptAssets(library: String, loader: String, app: String)
}

class BoatHtml(jsFiles: ScriptAssets, assets: AssetsSource = HashedAssetsSource)
  extends Bootstrap(HtmlTags) {
  val reverse = Reverse
//  val mapboxVersion = AppMeta.default.mapboxVersion

  implicit def wrapFrag[T <: WrappedString](w: T): StringFrag = stringFrag(w.value)
  implicit def wrapAttr[T <: WrappedString]: AttrValue[T] = BoatImplicits.boatStringAttr(_.value)

  def devices(user: UserInfo) =
    page(PageConf(BoatsPage(user), bodyClasses = Seq(BoatsClass)))

  def tracks(data: TracksBundle, query: TrackQuery, lang: Lang) =
    page(PageConf(TracksPage(data, query, lang), bodyClasses = Seq(StatsClass)))

  def signIn(lang: Lang) = page(PageConf(SignInPage(lang.settings)))

  def list(track: FullTrack, current: Limits, lang: BoatLang) =
    page(PageConf(SentencesPage(track, current, lang), bodyClasses = Seq(FormsClass)))

  def chart(track: TrackRef, lang: BoatLang) =
    page(Charts.chart(track, lang))

  def map(ub: UserBoats) = {
    val lang = BoatLang(ub.language)
    val about = About(lang.web, lang.lang.profile)
    val user = ub.user
    val isAnon = user == Usernames.anon
    val mapClass = if (ub.boats.isEmpty) "anon" else "auth"
    page(
      PageConf(
        modifier(
          ub.boats.headOption.map { b =>
            modifier(
              div(id := "navbar", `class` := "navbar navbar-boat py-1")(
                a(`class` := "nav-text tight boats-link", href := reverse.boats)(b.boat),
                div(`class` := "dropdown nav-text tight tracks", id := BoatDropdownId)(
                  span(`class` := "dropdown-button", ""),
                  div(`class` := "dropdown-content", id := BoatDropdownContentId)(
                    ub.boats.map { boat =>
                      a(
                        `class` := s"track-link $DeviceLinkClass",
                        href := "#",
                        data("name") := s"${boat.boat}"
                      )(boat.boat)
                    }
                  )
                ),
                a(
                  href := reverse.tracks,
                  `class` := "icon-link history",
                  title := lang.lang.track.tracks
                ),
                div(`class` := "dropdown nav-text tracks", id := DropdownLinkId)(
                  span(`class` := "dropdown-button", lang.lang.track.tracks),
                  div(`class` := "dropdown-content", id := DropdownContentId)(
                    b.tracks.map { t =>
                      a(`class` := "track-link", href := reverse.canonical(t.canonical))(
                        span(t.describe),
                        span(short(t.distanceMeters)),
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
                standaloneQuestion("boat-icon framed question")
              }
            )
          },
          div(id := MapId, `class` := s"mapbox-map $mapClass"),
          about.about(user, ub.language)
        ),
        bodyClasses = Seq(s"$MapClass $AboutClass")
      )
    )
  }

  def routeContainer = div(id := RoutesContainer, `class` := RoutesContainer)(
    span(id := RouteLength, `class` := "nav-text route-length")(""),
    span(id := RouteText, `class` := "nav-text route-text")("")
  )

  def short(d: DistanceM) =
    if (d.toKilometers >= 10) s"${d.toKilometers.toInt} km"
    else if (d.toMeters >= 10) s"${d.toMeters.toInt} m"
    else s"${d.toMillis.toInt} mm"

  def standaloneQuestion(cls: String) =
    fontAwesomeLink(span, Question, "question", cls, "About")

  def personIcon(cls: String) =
    fontAwesomeLink(a, PersonLink, "user", cls, "Sign in", href := reverse.signIn)

  def fontAwesomeLink(
    tag: ConcreteHtmlTag[String],
    idValue: String,
    faIcon: String,
    classes: String,
    titleValue: String,
    more: AttrPair*
  ) =
    tag(
      id := idValue,
      `class` := s"icon-link $faIcon $classes",
      title := titleValue,
      aria.hidden := "true",
      more
    )

  def page(pageConf: PageConf) = TagPage(
    html(
      head(
        meta(charset := "utf-8"),
        meta(
          name := "description",
          content := "Free nautical charts for Finland with live AIS tracking."
        ),
        meta(
          name := "keywords",
          content := "charts, nautical, boat, tracking, ais, live, vessels, marine"
        ),
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
          script(`type` := "text/javascript", src := versioned(jsFile))
        }
      )
    )
  )

  def versioned(file: String): Uri = assets.at(file)
}
