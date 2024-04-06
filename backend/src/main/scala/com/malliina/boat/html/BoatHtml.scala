package com.malliina.boat.html

import com.malliina.assets.{FileAssets, HashedAssets}
import com.malliina.boat.FrontKeys.*
import com.malliina.boat.html.BoatImplicits.given
import com.malliina.boat.http.{Limits, TracksQuery}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{AppConf, BuildInfo, Coord, FormsLang, FrontKeys, FullTrack, Lang, Shortcut, SourceType, TrackRef, TrackShortcut, TracksBundle, UserBoats, UserInfo, Usernames}
import com.malliina.html.HtmlTags
import com.malliina.html.HtmlTags.{cssLink, deviceWidthViewport, titleTag}
import com.malliina.html.HtmlImplicits.given
import com.malliina.http.FullUrl
import com.malliina.live.LiveReload
import com.malliina.measure.DistanceM
import org.http4s.Uri
import scalatags.Text
import scalatags.Text.all.*

import scala.language.implicitConversions

object BoatHtml:
  def fromBuild(sourceType: SourceType): BoatHtml =
    default(BuildInfo.isProd, sourceType)

  private def chooseFavicon(sourceType: SourceType) =
    if sourceType == SourceType.Vehicle then FileAssets.img.favicon_car_svg
    else FileAssets.img.favicon_boat_png

  def faviconPath(sourceType: SourceType) = s"assets/${chooseFavicon(sourceType)}"

  def default(isProd: Boolean, sourceType: SourceType): BoatHtml =
    val externalScripts = if isProd then Nil else FullUrl.build(LiveReload.script).toSeq
    val pageTitle =
      if sourceType == SourceType.Vehicle then AppConf.CarName
      else s"${AppConf.Name} - Free nautical charts for Finland"
    val appScripts =
      if isProd then Seq(FileAssets.frontend_js)
      else Seq(FileAssets.frontend_js, FileAssets.frontend_loader_js, FileAssets.main_js)
    BoatHtml(
      appScripts,
      externalScripts,
      Seq(FileAssets.frontend_css, FileAssets.fonts_css, FileAssets.styles_css),
      AssetsSource(isProd),
      chooseFavicon(sourceType),
      pageTitle
    )

class BoatHtml(
  jsFiles: Seq[String],
  externalScripts: Seq[FullUrl],
  cssFiles: Seq[String],
  assets: AssetsSource,
  favicon: String,
  pageTitle: String
):
  val reverse = Reverse

  def devices(user: UserInfo) =
    page(PageConf(BoatsPage(user), Seq(BoatsClass)))

  def tracks(user: UserInfo, data: TracksBundle, query: TracksQuery, lang: Lang): Frag =
    page(PageConf(TracksPage(user, data, query, lang), Seq(StatsClass)))

  def signIn(lang: Lang) = page(
    PageConf(SignInPage(lang.settings))
  )

  def list(track: FullTrack, current: Limits, lang: BoatLang) =
    page(PageConf(SentencesPage(track, current, lang), Seq(FormsClass)))

  def chart(track: TrackRef, lang: BoatLang) =
    page(Charts.chart(track, lang))

  def privacyPolicy = page(PageConf(PrivacyPolicy.page))

  def map(ub: UserBoats, center: Option[Coord] = None) =
    val boatLang = BoatLang(ub.language)
    val lang = boatLang.lang
    val about = About(boatLang.web, lang.profile)
    val user = ub.user
    val isAnon = user == Usernames.anon
    val mapClass = if ub.boats.isEmpty then "anon" else "auth"
    page(
      PageConf(
        modifier(
          center.fold(modifier()): coord =>
            span(
              id := FrontKeys.Center,
              data(FrontKeys.Lng) := coord.lng.lng,
              data(FrontKeys.Lat) := coord.lat.lat
            ),
          ub.boats.headOption
            .map: b =>
              modifier(
                div(id := "navbar", cls := "navbar navbar-boat py-1")(
                  a(cls := "nav-text tight boats-link", href := reverse.boats)(b.boat),
                  div(cls := "dropdown nav-text tight tracks", id := BoatDropdownId)(
                    span(cls := "dropdown-button", ""),
                    div(cls := "dropdown-content", id := BoatDropdownContentId)(
                      ub.boats.map: boat =>
                        a(
                          cls := s"track-link $DeviceLinkClass",
                          href := "#",
                          data("name") := s"${boat.boat}"
                        )(boat.boat)
                    )
                  ),
                  a(
                    href := reverse.tracks,
                    cls := "icon-link history",
                    title := lang.track.tracks
                  ),
                  div(cls := "dropdown nav-text tracks", id := DropdownLinkId)(
                    span(cls := "dropdown-button", lang.track.tracks),
                    div(cls := "dropdown-content", id := DropdownContentId)(
                      ub.boats
                        .flatMap(_.tracks)
                        .sortBy(_.times.start.millis)
                        .reverse
                        .map: t =>
                          a(cls := "track-link", href := reverse.canonical(t.canonical))(
                            span(t.describe),
                            span(short(t.distanceMeters)),
                            span(t.times.range)
                          )
                    )
                  ),
                  span(id := TitleId, cls := "nav-text title"),
                  span(id := DistanceId, cls := "nav-text distance"),
                  span(id := ConsumptionId, cls := "nav-text consumption"),
                  span(id := DurationId, cls := "nav-text duration"),
                  span(id := TopSpeedId, cls := "nav-text top-speed"),
                  span(id := WaterTempId, cls := "nav-text water-temp"),
                  fontAwesomeLink(a, FullLinkId, "list", Hidden, "List"),
                  fontAwesomeLink(a, GraphLinkId, "chart-area", Hidden, "Graph"),
                  standaloneQuestion("question-nav")
                ),
                datesContainer(lang.settings.forms),
                routeContainer
              )
            .getOrElse:
              modifier(
                routeContainer,
                if isAnon then
                  modifier(
                    standaloneQuestion("boat-icon framed question"),
                    personIcon("boat-icon framed user")
                  )
                else standaloneQuestion("boat-icon framed question")
              )
          ,
          div(id := MapId, cls := s"mapbox-map $mapClass"),
          about.about(user, ub.language)
        ),
        bodyClasses = Seq(s"$MapClass $AboutClass")
      )
    )

  private def routeContainer = div(id := RoutesContainer, cls := RoutesContainer)(
    span(id := RouteLength, cls := "nav-text route-length")(""),
    span(id := RouteText, cls := "nav-text route-text")("")
  )

  private def datesContainer(formsLang: FormsLang) =
    div(id := DatesContainer, cls := s"row $DatesContainer")(
      timePicker(formsLang.from, FromTimePickerId, "me-2"),
      timePicker(formsLang.to, ToTimePickerId, "me-2"),
      div(cls := "time-shortcuts time-picker-container col-sm-6 col-md-4 mt-2 mb-0 mt-sm-0")(
        select(
          id := ShortcutsId,
          cls := "form-select form-select-sm",
          aria.label := "Select time shortcut"
        )(
          option(selected)(formsLang.shortcuts),
          Seq(TrackShortcut.Latest -> formsLang.latest, TrackShortcut.Latest5 -> formsLang.latest5)
            .map((shortcut, word) => option(value := shortcut)(word)),
          Seq(
            Shortcut.Last30min -> formsLang.last30min,
            Shortcut.Last2h -> formsLang.last2h,
            Shortcut.Last12h -> formsLang.last12h,
            Shortcut.Last24h -> formsLang.last24h,
            Shortcut.Last48h -> formsLang.last48h
          ).map((shortcut, word) => option(value := shortcut)(word))
        )
      ),
      div(id := LoadingSpinnerId, cls := "loader col-sm-6 col-md-4 mx-2 mb-0 mt-sm-0")
    )

  private def timePicker(labelText: String, divId: String, clazz: String) =
    val inputId = s"$divId-input"
    div(cls := s"time-picker-container col-sm-6 col-md-4 mt-2 mb-0 mt-sm-0 $clazz")(
      div(
        id := divId,
        data("td-target-input") := "nearest",
        cls := "input-group input-group-sm"
      )(
        label(`for` := inputId, cls := "input-group-text")(labelText),
        input(
          id := inputId,
          cls := "form-control",
          data("td-target") := s"#$divId"
        ),
        span(
          cls := "input-group-text",
          data("td-target") := s"#$divId",
          data("td-toggle") := "datetimepicker"
        )(
          span(cls := "time-calendar")
        )
      )
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
      cls := s"icon-link $faIcon $classes",
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
        titleTag(pageTitle),
        deviceWidthViewport,
        StructuredData.appStructuredData,
        StructuredData.appLinkMetadata,
        link(rel := "icon", `type` := "image/png", href := inlineOrAsset(favicon)),
        cssFiles.map: file =>
          cssLink(versioned(file))
      ),
      body(cls := pageConf.bodyClasses.mkString(" "))(
        pageConf.content,
        jsFiles.map: jsFile =>
          script(`type` := "text/javascript", src := versioned(jsFile)),
        externalScripts.map: url =>
          script(src := url, defer)
      )
    )

  private def inlineOrAsset(file: String) =
    HashedAssets.dataUris.getOrElse(file, versioned(file).toString)
  private def versioned(file: String): Uri = assets.at(file)
