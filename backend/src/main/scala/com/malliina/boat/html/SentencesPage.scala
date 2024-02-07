package com.malliina.boat.html

import com.malliina.boat.FrontKeys.*
import com.malliina.boat.http.Limits
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{BoatFormats, FullTrack, TrackComments, TrackName, TrackRef, TrackTitle}
import com.malliina.measure.SpeedM
import scalatags.Text.all.*

import scala.language.implicitConversions

object SentencesPage extends BoatImplicits:
  val reverse = Reverse

  def apply(track: FullTrack, current: Limits, lang: BoatLang): Modifier =
    val speedUnit = BoatFormats.speedUnit(track.track.sourceType)
    val trackLang = lang.lang.track
    div(cls := "container")(
      namedInfoBox(track.track, lang),
      pagination(track.track, current),
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
      pagination(track.track, current)
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

  private def pagination(track: TrackRef, current: Limits) =
    val pageSize = 100
    val trackName = track.trackName
    val hasMore = current.offset + pageSize < track.points
    val prevOffset = math.max(current.offset - pageSize, 0)
    val lastOffset = ((track.points - 1) / pageSize) * pageSize
    tag("nav")(aria.label := "Navigation")(
      ul(cls := "pagination justify-content-center")(
        pageLink(trackName, Limits(pageSize, 0), "First", isDisabled = current.offset == 0),
        pageLink(
          trackName,
          current.copy(offset = prevOffset),
          "Previous",
          isDisabled = current.offset == 0
        ),
        pageLink(trackName, current, "" + current.page, isActive = true),
        pageLink(
          trackName,
          current.copy(offset = current.offset + pageSize),
          "Next",
          isDisabled = !hasMore
        ),
        pageLink(
          trackName,
          Limits(pageSize, lastOffset),
          "Last",
          isDisabled = lastOffset == current.offset
        )
      )
    )

  private def pageLink(
    track: TrackName,
    to: Limits,
    text: String,
    isActive: Boolean = false,
    isDisabled: Boolean = false
  ) =
    val params = Map(Limits.Limit -> s"${to.limit}", Limits.Offset -> s"${to.offset}")
    val call = reverse.trackFull(track).withQueryParams(params)
    val liClass = if isDisabled then "disabled" else ""
    val _ = if isActive then "todo" else "todo"
    li(cls := classes("page-item", liClass))(a(cls := "page-link", href := call)(text))

  def classes(cs: String*) = cs.filter(_.nonEmpty).mkString(" ")
