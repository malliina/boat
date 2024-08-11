package com.malliina.boat.html

import com.malliina.boat.FrontKeys.*
import com.malliina.boat.http.Limits
import com.malliina.boat.{BoatFormats, FullTrack, TrackComments, TrackRef, TrackTitle}
import com.malliina.measure.SpeedM
import scalatags.Text.all.*

import scala.language.implicitConversions

object SentencesPage extends BoatSyntax:
  def apply(track: FullTrack, current: Limits, lang: BoatLang): Modifier =
    val speedUnit = BoatFormats.speedUnit(track.track.sourceType)
    val trackLang = lang.lang.track
    val pagination = Pagination(lang.web.pagination)
    div(cls := "container")(
      namedInfoBox(track.track, lang),
      pagination.tracks(track.track, current),
      table(cls := "table table-hover")(
        thead(
          tr(th(trackLang.coordinate), th(s"${trackLang.speed} ($speedUnit)"), th(lang.lang.time))
        ),
        tbody(
          track.coords.map: c =>
            modifier(
              tr(td(c.coord.approx), td(c.boatSpeed), td(c.time.dateTime)),
              c.sentences.map: sentence =>
                tr(cls := "row-sm")(
                  td(colspan := 2)(sentence.sentence),
                  td(sentence.time.dateTime)
                )
            )
        )
      ),
      pagination.tracks(track.track, current)
    )

  def namedInfoBox(track: TrackRef, lang: BoatLang): Modifier =
    val trackLang = lang.lang.track
    val webLang = lang.web
    val topSpeed = track.topSpeed.getOrElse(SpeedM.zero)
    val commentValue = track.comments.getOrElse("")
    modifier(
      div(cls := "row")(
        div(cls := "col-md-12")(
          h1(track.boatName)
        )
      ),
      div(cls := "mb-3")(
        form(
          cls := Hidden,
          id := EditTitleFormId,
          method := "PUT",
          action := reverse.modifyTitle(track.trackName)
        )(
          div(cls := "form-group row form-title")(
            label(`for` := TitleInputId, cls := "col-sm-2 col-form-label col-form-label-sm")(
              webLang.editTitle
            ),
            div(cls := "col-sm-7 pr-2")(
              input(
                `type` := "text",
                id := TitleInputId,
                name := TrackTitle.Key,
                cls := "form-control form-control-sm input-title",
                track.trackTitle.map(t => value := t).getOrElse(modifier()),
                placeholder := webLang.titlePlaceholder
              )
            ),
            div(cls := "col-3 px-0")(
              button(`type` := "submit", cls := "btn btn-sm btn-primary mx-1")(webLang.save),
              button(
                `type` := "button",
                id := CancelEditTrackId,
                cls := "btn btn-sm btn-secondary mx-1"
              )(webLang.cancel)
            )
          )
        ),
        dl(cls := "row mb-0")(
          dt(cls := s"col-sm-2 $TrackRow")(trackLang.track),
          dd(cls := s"col-sm-10 $TrackRow")(
            span(cls := "text-editable", id := TrackTitleId)(track.describe),
            editIcon(EditTitleId, webLang.editTitle)
          ),
          description(lang.lang.time, track.times.range),
          description(trackLang.duration, BoatFormats.formatDuration(track.duration)),
          description(trackLang.topSpeed, topSpeed),
          dt(cls := s"col-sm-2 $CommentsRow")(trackLang.comments),
          dd(cls := s"col-sm-10 $CommentsRow")(
            span(
              cls := classes("text-editable", track.comments.fold(Hidden)(_ => "")),
              id := CommentsTitleId
            )(commentValue),
            editIcon(EditCommentsId, webLang.editComments)
          )
        ),
        form(
          cls := s"$Hidden mb-3",
          id := EditCommentsFormId,
          method := "PATCH",
          action := reverse.updateComments(track.track)
        )(
          div(cls := "form-group row form-title")(
            label(`for` := CommentsInputId, cls := "col-sm-2 col-form-label col-form-label-sm")(
              webLang.editComments
            ),
            div(cls := "col-sm-7 pr-2")(
              input(
                `type` := "text",
                id := CommentsInputId,
                name := TrackComments.Key,
                cls := "form-control form-control-sm input-title",
                track.comments.map(c => value := c).getOrElse(modifier()),
                placeholder := webLang.commentsPlaceholder
              )
            ),
            div(cls := "col-3 px-0")(
              button(`type` := "submit", cls := "btn btn-sm btn-primary mx-1")(webLang.save),
              button(
                `type` := "button",
                id := CancelEditCommentsId,
                cls := "btn btn-sm btn-secondary mx-1"
              )(webLang.cancel)
            )
          )
        )
      )
    )

  private def editIcon(editId: String, titleText: String) =
    i(
      id := editId,
      cls := "icon-link edit-icon input-title tight",
      title := titleText,
      aria.hidden := "true"
    )

  private def description(labelText: String, value: Modifier) = modifier(
    dt(cls := "col-sm-2")(labelText),
    dd(cls := "col-sm-10")(value)
  )
