package com.malliina.boat.html

import java.time.Instant

import com.malliina.boat.BoatFormats._
import com.malliina.boat.FrontKeys._
import com.malliina.boat.http.Limits
import com.malliina.boat.{BoatFormats, FullTrack, Instants, TrackName, TrackRef, TrackTitle}
import com.malliina.measure.{Distance, Speed, Temperature}
import com.malliina.values.Wrapped
import controllers.routes
import play.api.mvc.Call
import scalatags.Text.all._

import scala.language.implicitConversions

object SentencesPage {
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
      form(`class` := Hidden, id := EditTitleFormId, method := "PUT", action := routes.BoatController.modifyTitle(track.trackName))(
        div(`class` := "form-group row form-title")(
          label(`for` := "title", `class` := "col-sm-2 col-form-label col-form-label-sm")("Edit title"),
          div(`class` := "col-sm-7 pr-2")(
            input(`type` := "text", id := TitleInputId, name := TrackTitle.Key,
              `class` := "form-control form-control-sm input-title",
              track.trackTitle.map(t => value := t.title).getOrElse(modifier()),
              placeholder := "Evening trip")
          ),
          div(`class` := "col-3 pl-0")(
            button(`type` := "submit", `class` := "btn btn-sm btn-primary")("Save"),
            button(`type` := "button", id := CancelEditTrackId, `class` := "btn btn-sm btn-secondary ml-1")("Cancel")
          )
        )
      ),
      dl(`class` := "row")(
        dt(`class` := s"col-sm-2 $TrackRow")("Track"),
        dd(`class` := s"col-sm-10 $TrackRow")(span(id := TrackTitleId)(track.describe), editIcon),
        description("Time", track.startEndRange),
        description("Duration", BoatFormats.formatDuration(track.duration)),
        description("Top speed", topSpeed),
      )
    )
  }

  def editIcon = span(
    id := EditTitleId, `class` := s"oi icon-link pencil",
    data("glyph") := "pencil", title := "Edit track title",
    aria.hidden := "true")

  private def description(labelText: String, value: Modifier) = modifier(
    dt(`class` := "col-sm-2")(labelText),
    dd(`class` := "col-sm-10")(value)
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
