package com.malliina.boat.html

import cats.implicits.toShow
import com.malliina.boat.BoatFormats.{durationHuman, formatDistance, inHours}
import com.malliina.boat.FrontKeys.{Hidden, MonthRow, YearData, YearRow}
import com.malliina.boat.http.{Limits, SortOrder, TrackSort, TracksQuery}
import com.malliina.boat.{BoatLang as _, *}
import com.malliina.measure.DistanceM
import org.http4s.Uri
import scalatags.Text
import scalatags.Text.all.*

import scala.language.implicitConversions

object TracksPage extends BoatSyntax:
  private val monthDataAttr = data("month")
  private val yearDataAttr = data(YearData)

  implicit def distanceKmHtml(d: DistanceM): Frag = stringFrag(formatDistance(d) + " km")

  private def translate(month: MonthVal, lang: MonthsLang): String =
    month.month match
      case 1  => lang.jan
      case 2  => lang.feb
      case 3  => lang.mar
      case 4  => lang.apr
      case 5  => lang.may
      case 6  => lang.jun
      case 7  => lang.jul
      case 8  => lang.aug
      case 9  => lang.sep
      case 10 => lang.oct
      case 11 => lang.nov
      case 12 => lang.dec
      case _  => ""

  def apply(user: UserInfo, tracks: TracksBundle, tracksQuery: TracksQuery, blang: BoatLang) =
    val lang = blang.lang
    val trackLang = lang.track
    val stats = tracks.stats
    val allTime = stats.allTime
    val pagination = Pagination(blang.web.pagination)

    div(cls := "container")(
      div(cls := "row")(
        div(cls := "col-md-12")(
          h1(trackLang.trackHistory)
        )
      ),
      div(cls := "row mb-4")(
        div(cls := "col-md-12")(
          user.boats.zipWithIndex.map: (boat, idx) =>
            val extra = if idx == 0 then "me-1" else "mx-1"
            val sources = tracksQuery.sources
            val isSelected = sources.contains(boat.name)
            val buttonCls =
              if isSelected then "btn-primary"
              else "btn-outline-primary"
            val linkQuery = tracksQuery.copy(sources =
              if isSelected then sources.filter(s => s != boat.name) else sources :+ boat.name
            )
            a(
              cls := s"btn $buttonCls $extra",
              href := withQuery(reverse.tracks, queryParams(linkQuery))
            )(boat.name)
        )
      ),
      div(cls := "row")(
        div(cls := "col-md-12")(
          h3(lang.labels.statistics)
        )
      ),
      div(cls := "row mb-4")(
        div(cls := "col-xl-8")(
          table(cls := "table table-hover")(
            thead(
              tr(
                th(lang.time),
                th(trackLang.distance),
                th(trackLang.duration),
                th(trackLang.hours),
                th(trackLang.routes),
                th(trackLang.days)
              )
            ),
            tbody(
              stats.yearly.map: year =>
                modifier(
                  tr(cls := YearRow, yearDataAttr := year.year)(
                    td(year.year),
                    td(year.distance),
                    td(durationHuman(year.duration)),
                    td(inHours(year.duration)),
                    td(year.trackCount),
                    td(year.days)
                  ),
                  year.monthly.map: month =>
                    tr(
                      cls := s"$MonthRow $Hidden",
                      yearDataAttr := year.year,
                      monthDataAttr := month.month
                    )(
                      td(translate(month.month, lang.calendar.months)),
                      td(month.distance),
                      td(durationHuman(month.duration)),
                      td(inHours(month.duration)),
                      td(month.trackCount),
                      td(month.days)
                    )
                ),
              tr(
                td(lang.labels.allTime),
                td(allTime.distance),
                td(durationHuman(allTime.duration)),
                td(inHours(allTime.duration)),
                td(allTime.trackCount),
                td(allTime.days)
              )
            )
          )
        )
      ),
      div(cls := "row")(
        div(cls := "col-md-12")(
          h3(lang.track.tracks)
        )
      ),
      if tracks.tracks.nonEmpty then
        val limits = tracksQuery.query.limits
        val paginationControl = pagination(
          linkUri(tracksQuery),
          hasMore = tracks.tracks.size >= limits.limit,
          lastOffset = None,
          current = limits
        )
        modifier(
          paginationControl,
          table(cls := "table table-hover")(
            thead(
              tr(
                th(lang.settings.boatLang.boat),
                column(lang.name, TrackSort.Name, tracksQuery),
                column(trackLang.date, TrackSort.Recent, tracksQuery),
                column(trackLang.duration, TrackSort.Time, tracksQuery),
                column(trackLang.distance, TrackSort.Length, tracksQuery),
                column(trackLang.topSpeed, TrackSort.TopSpeed, tracksQuery)
              )
            ),
            tbody(
              tracks.tracks.map: track =>
                val speed: String =
                  track.topSpeed
                    .map(BoatFormats.formatSpeed(_, track.sourceType, includeUnit = true))
                    .getOrElse(lang.messages.notAvailable)
                tr(
                  td(track.boatName),
                  td(a(href := reverse.canonical(track.canonical))(track.describe)),
                  td(track.times.range),
                  td(BoatFormats.formatDuration(track.duration)),
                  td(track.distanceMeters),
                  td(speed)
                )
            )
          ),
          paginationControl
        )
      else p(lang.messages.noSavedTracks)
    )

  def column(name: Modifier, sort: TrackSort, query: TracksQuery) =
    val isAsc = query.query.order == SortOrder.Asc
    val isActive = sort == query.query.sort
    val icon =
      if isActive then if isAsc then "chevron-up" else "chevron-down"
      else "sort"
    val inverseOrder = if isAsc then SortOrder.Desc else SortOrder.Asc
    val linkQuery = query.copy(query = query.query.copy(sort = sort, order = inverseOrder))
    th(
      linkTo(linkQuery)(
        name,
        " ",
        i(cls := s"icon-button $icon")
      )
    )

  private def linkTo(q: TracksQuery) = a(href := linkUri(q))

  private def linkUri(q: TracksQuery) = withQuery(reverse.tracks, queryParams(q))

  private def queryParams(q: TracksQuery) =
    val query = q.query
    val limits = query.limits
    Seq(
      TrackSort.key -> query.sort.name,
      SortOrder.key -> query.order.name,
      Limits.Limit -> s"${limits.limit}",
      Limits.Offset -> s"${limits.offset}"
    )
      .map((k, v) => k -> Seq(v))
      .toMap ++ boatsQueryOf(q.sources)

  private def boatsQueryOf(boats: Seq[BoatName]) =
    if boats.nonEmpty then Map(TracksQuery.BoatsKey -> boats.map(n => n.show))
    else Map.empty

  private def withQuery(call: Uri, params: Map[String, Seq[String]]): Uri =
    call.withMultiValueQueryParams(params)
