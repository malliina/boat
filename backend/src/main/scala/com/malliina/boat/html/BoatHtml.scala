package com.malliina.boat.html

import com.malliina.assets.HashedAssets
import com.malliina.boat.FrontKeys.*
import com.malliina.boat.http.{Limits, TrackQuery}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{AppConf, AppMode, BuildInfo, CarDrive, Coord, FrontKeys, FullTrack, Lang, TrackRef, TracksBundle, UserBoats, UserInfo, Usernames}
import com.malliina.html.HtmlTags.{cssLink, deviceWidthViewport, fullUrl, titleTag}
import com.malliina.html.{Bootstrap, HtmlTags}
import com.malliina.http.FullUrl
import com.malliina.live.LiveReload
import com.malliina.measure.DistanceM
import com.malliina.util.AppLogger
import com.malliina.values.WrappedString
import org.http4s.Uri
import scalatags.Text.all.*
import scalatags.Text

import scala.language.implicitConversions

object BoatHtml:
  def fromBuild: BoatHtml = apply(BuildInfo.isProd)

  val faviconPath = "assets/img/favicon.png"

  def apply(isProd: Boolean): BoatHtml =
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    val appScripts = Seq("frontend.js")
    new BoatHtml(
      appScripts,
      externalScripts,
      Seq("frontend.css", "fonts.css", "styles.css"),
      AssetsSource(isProd)
    )

class BoatHtml(
  jsFiles: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource
):
  val reverse = Reverse

  implicit def wrapFrag[T <: WrappedString](w: T): Modifier = stringFrag(w.value)
  implicit def wrapAttr[T <: WrappedString]: AttrValue[T] = BoatImplicits.boatStringAttr(_.value)

  def carHistory(drives: List[CarDrive]) =
    page(PageConf(CarHistoryPage(drives)))

  def devices(user: UserInfo) =
    page(PageConf(BoatsPage(user), bodyClasses = Seq(BoatsClass)))

  def tracks(data: TracksBundle, query: TrackQuery, lang: Lang): Frag =
    page(PageConf(TracksPage(data, query, lang), bodyClasses = Seq(StatsClass)))

  def signIn(lang: Lang) = page(PageConf(SignInPage(lang.settings)))

  def list(track: FullTrack, current: Limits, lang: BoatLang) =
    page(PageConf(SentencesPage(track, current, lang), bodyClasses = Seq(FormsClass)))

  def chart(track: TrackRef, lang: BoatLang) =
    page(Charts.chart(track, lang))

  def map(ub: UserBoats, center: Option[Coord] = None) =
    val lang = BoatLang(ub.language)
    val about = About(lang.web, lang.lang.profile)
    val user = ub.user
    val isAnon = user == Usernames.anon
    val mapClass = if ub.boats.isEmpty then "anon" else "auth"
    page(
      PageConf(
        modifier(
          center.fold(modifier()) { coord =>
            span(
              id := FrontKeys.Center,
              data(FrontKeys.Lng) := coord.lng.lng,
              data(FrontKeys.Lat) := coord.lat.lat
            )
          },
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
              if isAnon then
                modifier(
                  standaloneQuestion("boat-icon framed question"),
                  personIcon("boat-icon framed user")
                )
              else standaloneQuestion("boat-icon framed question")
            )
          },
          div(id := MapId, `class` := s"mapbox-map $mapClass"),
          about.about(user, ub.language)
        ),
        bodyClasses = Seq(s"$MapClass $AboutClass")
      )
    )

  private def routeContainer = div(id := RoutesContainer, `class` := RoutesContainer)(
    span(id := RouteLength, `class` := "nav-text route-length")(""),
    span(id := RouteText, `class` := "nav-text route-text")("")
  )

  def short(d: DistanceM) =
    if d.toKilometers >= 10 then s"${d.toKilometers.toInt} km"
    else if d.toMeters >= 10 then s"${d.toMeters.toInt} m"
    else s"${d.toMillis.toInt} mm"

  private def standaloneQuestion(cls: String) =
    fontAwesomeLink(span, Question, "question", cls, "About")

  private def personIcon(cls: String) =
    fontAwesomeLink(a, PersonLink, "user", cls, "Sign in", href := reverse.signIn)

  private def fontAwesomeLink(
    tag: ConcreteHtmlTag[String],
    idValue: String,
    faIcon: String,
    classes: String,
    titleValue: String,
    more: AttrPair*
  ): Frag =
    tag(
      id := idValue,
      `class` := s"icon-link $faIcon $classes",
      title := titleValue,
      aria.hidden := "true",
      more
    )

  def page(pageConf: PageConf): Frag =
    html(lang := "en")(
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
        link(rel := "icon", `type` := "image/png", href := inlineOrAsset("img/favicon.png")),
        cssFiles.map { file =>
          cssLink(versioned(file))
        }
      ),
      body(`class` := pageConf.bodyClasses.mkString(" "))(
        pageConf.content,
        jsFiles.map { jsFile =>
          script(`type` := "text/javascript", src := versioned(jsFile))
        },
        externalScripts.map { url =>
          script(src := url, defer)
        }
      )
    )

  private def inlineOrAsset(file: String) =
    HashedAssets.dataUris.getOrElse(file, versioned(file).toString)
  private def versioned(file: String): Uri = assets.at(file)
