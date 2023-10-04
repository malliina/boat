package com.malliina.boat.html

import com.malliina.boat.BoatFormats.{durationHuman, formatDistance, inHours}
import com.malliina.boat.FrontKeys.Hidden
import com.malliina.boat.http.{SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{BoatFormats, Lang, MonthVal, MonthsLang, TracksBundle}
import com.malliina.measure.DistanceM
import org.http4s.Uri
import scalatags.Text
import scalatags.Text.all.*

import scala.language.implicitConversions

object TracksPage extends BoatImplicits:
  val reverse = Reverse
  val monthDataAttr = data("month")
  val yearDataAttr = data("year")

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

  def apply(tracks: TracksBundle, query: TrackQuery, lang: Lang) =
    val sort = query.sort
    val order = query.order
    val isAsc = order == SortOrder.Asc
    val trackLang = lang.track
    val stats = tracks.stats
    val allTime = stats.allTime

    div(`class` := "container")(
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(lang.track.trackHistory)
        )
      ),
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h3(lang.labels.statistics)
        )
      ),
      div(`class` := "row")(
        div(`class` := "col-xl-8")(
          table(`class` := "table table-hover")(
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
              stats.yearly.map { year =>
                modifier(
                  tr(`class` := "year-row", yearDataAttr := year.year)(
                    td(year.year),
                    td(year.distance),
                    td(durationHuman(year.duration)),
                    td(inHours(year.duration)),
                    td(year.trackCount),
                    td(year.days)
                  ),
                  year.monthly.map { month =>
                    tr(
                      `class` := s"month-row $Hidden",
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
                  }
                )
              },
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
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h3(lang.track.tracks)
        )
      ),
      table(`class` := "table table-hover")(
        thead(
          tr(
            column(lang.name, TrackSort.Name, sort, isAsc),
            column(trackLang.date, TrackSort.Recent, sort, isAsc),
            column(trackLang.duration, TrackSort.Time, sort, isAsc),
            column(trackLang.distance, TrackSort.Length, sort, isAsc),
            column(trackLang.topSpeed, TrackSort.TopSpeed, sort, isAsc)
          )
        ),
        tbody(
          tracks.tracks.map { track =>
            val speed: String =
              track.topSpeed.map(BoatFormats.formatSpeed).getOrElse(lang.messages.notAvailable)
            tr(
              td(a(href := reverse.canonical(track.canonical))(track.describe)),
              td(track.times.range),
              td(BoatFormats.formatDuration(track.duration)),
              td(track.distanceMeters),
              td(speed)
            )
          }
        )
      )
    )

  def column(name: Modifier, sort: TrackSort, activeSort: TrackSort, isAsc: Boolean) =
    val isActive = sort == activeSort
    val mod =
      if isActive then if isAsc then "chevron-up" else "chevron-down"
      else "sort"
    val inverseOrder = if isAsc then SortOrder.Desc.name else SortOrder.Asc.name
    th(
      a(
        href := withQuery(
          reverse.tracks,
          Map(TrackSort.key -> sort.name, SortOrder.key -> inverseOrder)
        )
      )(name, " ", i(`class` := s"fas fa-$mod"))
    )

  def withQuery(call: Uri, params: Map[String, String]) =
    call.withQueryParams(params)
