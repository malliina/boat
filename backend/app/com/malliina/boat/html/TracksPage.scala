package com.malliina.boat.html

import com.malliina.boat.http.{SortOrder, TrackQuery, TrackSort}
import com.malliina.boat.{BoatFormats, Lang, TrackRef}
import controllers.routes
import play.api.mvc.Call
import scalatags.Text
import scalatags.Text.all._

object TracksPage extends BoatImplicits {
  val reverse = routes.BoatController

  def apply(tracks: Seq[TrackRef], query: TrackQuery, lang: Lang): Text.TypedTag[String] = {
    val sort = query.sort
    val order = query.order
    val isAsc = order == SortOrder.Asc
    val trackLang = lang.track
    div(`class` := "container")(
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(lang.track.tracks)
        )
      ),
      table(`class` := "table table-hover")(
        thead(
          tr(
            column(lang.name, TrackSort.Name, isAsc),
            column(trackLang.date, TrackSort.Recent, isAsc),
            column(trackLang.distance, TrackSort.Length, isAsc),
            column(trackLang.topSpeed, TrackSort.TopSpeed, isAsc)
          )
        ),
        tbody(
          tracks.map { track =>
            val speed: String =
              track.topSpeed.map(BoatFormats.formatSpeed).getOrElse(lang.messages.notAvailable)
            tr(
              td(a(href := reverse.index().copy(url = track.canonical.name))(track.describe)),
              td(track.times.range),
              td(track.distanceMeters),
              td(speed)
            )
          }
        )
      )
    )
  }

  def column(name: Modifier, sort: TrackSort, isAsc: Boolean) = {
    val upOrDown = if (isAsc) "up" else "down"
    val inverseOrder = if (isAsc) SortOrder.Desc.name else SortOrder.Asc.name
    th(
      a(href := withQuery(reverse.tracks(),
                          Map(TrackSort.key -> sort.name, SortOrder.key -> inverseOrder)))(
        name,
        " ",
        i(`class` := s"fas fa-chevron-$upOrDown")))
  }

  def withQuery(call: Call, params: Map[String, String]) = {
    val kvs = params.map { case (k, v) => s"$k=$v" }.mkString("&")
    call.copy(url = s"${call.url}?$kvs")
  }
}
