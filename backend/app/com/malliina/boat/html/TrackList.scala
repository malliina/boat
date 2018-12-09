package com.malliina.boat.html

import java.time.Instant

import com.malliina.boat.BoatFormats._
import com.malliina.boat.http.Limits
import com.malliina.boat.{BoatFormats, FullTrack, Instants, TrackName, TrackRef}
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

  implicit def instantHtml(i: Instant): StringFrag = stringFrag(Instants.format(i).dateTime)

  implicit def wrappedHtml[T <: Wrapped](w: Wrapped): StringFrag = stringFrag(w.value)

  def apply(track: FullTrack, current: Limits): Modifier = {
    div(`class` := "container")(
      namedInfoBox(track.track),
      pagination(track.track, current),
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
      pagination(track.track, current)
    )
  }

  def namedInfoBox(track: TrackRef): Modifier = {
    val topSpeed: Speed = track.topSpeed.getOrElse(Speed.zero)
    modifier(
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(track.boatName)
        )
      ),
      dl(`class` := "row")(
        description("Track", track.trackName),
        description("Time", track.startEndRange),
        description("Duration", BoatFormats.formatDuration(track.duration)),
        description("Top speed", topSpeed),
      )
    )
  }

  private def description(labelText: String, value: Modifier) = modifier(
    dt(`class` := "col-sm-2")(labelText), dd(`class` := "col-sm-10")(value)
  )

  private def pagination(track: TrackRef, current: Limits) = {
    val pageSize = 100
    val trackName = track.trackName
    val hasMore = current.offset + pageSize < track.points
    val prevOffset = math.max(current.offset - pageSize, 0)
    val lastOffset = ((track.points - 1) / pageSize) * pageSize
    tag("nav")(aria.label := "Navigation")(
      ul(`class` := "pagination justify-content-center")(
        pageLink(trackName, Limits(pageSize, 0), "First", isDisabled = current.offset == 0),
        pageLink(trackName, current.copy(offset = prevOffset), "Previous", isDisabled = current.offset == 0),
        pageLink(trackName, current, "" + current.page, isActive = true),
        pageLink(trackName, current.copy(offset = current.offset + pageSize), "Next", isDisabled = !hasMore),
        pageLink(trackName, Limits(pageSize, lastOffset), "Last", isDisabled = lastOffset == current.offset)
      )
    )
  }

  private def pageLink(track: TrackName, to: Limits, text: String, isActive: Boolean = false, isDisabled: Boolean = false) = {
    val base = routes.BoatController.full(track)
    val call = base.copy(url = s"${base.url}?${Limits.Limit}=${to.limit}&${Limits.Offset}=${to.offset}")
    val liClass = if (isDisabled) "disabled" else ""
    li(`class` := classes("page-item", liClass))(a(`class` := "page-link", href := call)(text))
  }

  def classes(cs: String*) = cs.filter(_.nonEmpty).mkString(" ")
}
