package com.malliina.boat.html

import com.malliina.boat.FrontKeys.*
import com.malliina.boat.Latitude.lat
import com.malliina.boat.Longitude.lng
import com.malliina.boat.{AisLang, Coord, FormsLang, FrontKeys, Shortcut, TrackShortcut, UserBoats, Usernames}
import com.malliina.measure.DistanceM
import scalatags.Text.all.*

object MapPage extends BoatSyntax:
  def apply(ub: UserBoats, center: Option[Coord] = None) =
    val boatLang = BoatLang(ub.language)
    val lang = boatLang.lang
    val about = About(boatLang.web, lang.profile)
    val user = ub.user
    val isAnon = user == Usernames.anon
    val mapClass = if ub.boats.isEmpty then "anon" else "auth"
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
            filtersContainer(lang.settings.forms, lang.ais),
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
    )

  private def routeContainer = div(id := RoutesContainer, cls := RoutesContainer)(
    span(id := RouteLength, cls := "nav-text route-length")(""),
    span(id := RouteText, cls := "nav-text route-text")(""),
    span(id := RouteDuration, cls := "nav-text route-duration")("")
  )

  private def filtersContainer(formsLang: FormsLang, aisLang: AisLang) =
    div(id := DatesContainer, cls := s"row $DatesContainer")(
      timePicker(formsLang.from, FromTimePickerId, "me-2"),
      timePicker(formsLang.to, ToTimePickerId, "me-2"),
      shortcutsSelect(formsLang),
      vesselSearch(aisLang),
      div(id := LoadingSpinnerId, cls := "loader col-sm-6 col-md-4 mx-2 mb-0 mt-sm-0")
    )

  private def shortcutsSelect(formsLang: FormsLang) =
    div(cls := "time-shortcuts time-picker-container col-sm-6 col-md-4 mt-2 mb-0 mt-sm-0 me-2")(
      select(
        id := ShortcutsId,
        cls := "form-select form-select-sm",
        aria.label := "Select time shortcut"
      )(
        option(selected)(formsLang.shortcuts),
        (1 to 5).map: n =>
          option(value := TrackShortcut(n))(s"${formsLang.latestPlural} $n"),
        Seq(
          Shortcut.Last30min -> formsLang.last30min,
          Shortcut.Last2h -> formsLang.last2h,
          Shortcut.Last12h -> formsLang.last12h,
          Shortcut.Last24h -> formsLang.last24h,
          Shortcut.Last48h -> formsLang.last48h
        ).map((shortcut, word) => option(value := shortcut)(word))
      )
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
          aria.autocomplete := "none",
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

  private def vesselSearch(aisLang: AisLang) =
    div(
      cls := "autocomplete-container col-sm-6 col-md-4 mt-2 mb-0 mt-sm-0 me-2"
    )(
      input(
        id := VesselInputId,
        cls := "form-control form-control-sm",
        autocomplete := "off",
        aria.autocomplete := "none",
        placeholder := aisLang.searchPlaceholder,
        tpe := "text"
      )
    )

  private def short(d: DistanceM): String =
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
