package com.malliina.boat.html

import java.time.Instant

import com.malliina.boat.BoatFormats._
import com.malliina.boat.http.Limits
import com.malliina.boat.{BoatFormats, FullTrack, Instants, TrackName}
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values.Wrapped
import controllers.routes
import play.api.mvc.Call
import scalatags.Text.all._

import scala.language.implicitConversions

object TrackList {
  implicit val callAttr = genericAttr[Call]

  implicit def speedHtml(s: Speed): StringFrag = stringFrag(formatSpeed(s))

  implicit def distanceHtml(d: Distance): StringFrag = stringFrag(formatDistance(d))

  implicit def tempHtml(t: Temperature): StringFrag = stringFrag(formatTemp(t))

  implicit def instantHtml(i: Instant): StringFrag = stringFrag(Instants.format(i))

  implicit def wrappedHtml[T <: Wrapped](w: Wrapped): StringFrag = stringFrag(w.value)

  def apply(track: FullTrack, current: Limits): Modifier = {
    val topSpeed: Speed = track.track.topSpeed.getOrElse(Speed.zero)
    div(`class` := "container")(
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(track.track.boatName)
        )
      ),
      dl(`class` := "row")(
        description("Track", track.name),
        description("Time", track.track.startEndRange),
        description("Duration", BoatFormats.formatDuration(track.track.duration)),
        description("Top speed", topSpeed),
      ),
      pagination(track.name, current),
      table(`class` := "table table-hover")(
        thead(tr(th("Coordinate"), th("Speed"), th("Time"))),
        tbody(
          track.coords.map { c =>
            modifier(
              tr(td(c.coord.approx), td(c.boatSpeed), td(c.boatTime)),
              c.sentences.map { sentence =>
                tr(`class` := "row-sm")(td(colspan := 2)(sentence.sentence), td(sentence.added))
              }
            )
          }
        )
      ),
      pagination(track.name, current)
    )
  }

  private def description(labelText: String, value: Modifier) = modifier(
    dt(`class` := "col-sm-2")(labelText), dd(`class` := "col-sm-10")(value)
  )

  private def pagination(track: TrackName, current: Limits) = tag("nav")(aria.label := "Navigation")(
    ul(`class` := "pagination justify-content-center")(
      pageLink(track, current.copy(offset = math.max(current.offset - 100, 0)), "Previous"),
      pageLink(track, current, "" + current.page, isActive = true),
      pageLink(track, current.copy(offset = current.offset + 100), "Next")
    )
  )

  private def pageLink(track: TrackName, to: Limits, text: String, isActive: Boolean = false, isDisabled: Boolean = false) = {
    val base = routes.BoatController.full(track)
    val call = base.copy(url = s"${base.url}?${Limits.Limit}=${to.limit}&${Limits.Offset}=${to.offset}")
    val liClass = if (isDisabled) "disabled" else ""
    li(`class` := classes("page-item", liClass))(a(`class` := "page-link", href := call)(text))
  }

  def classes(cs: String*) = cs.filter(_.nonEmpty).mkString(" ")
}
