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
    val trackLang = lang.lang.track
    div(`class` := "container")(
      namedInfoBox(track.track, lang),
      pagination(track.track, current),
      table(`class` := "table table-hover")(
        thead(tr(th(trackLang.coordinate), th(trackLang.speed), th(lang.lang.time))),
        tbody(
          track.coords.map { c =>
            modifier(
              tr(td(c.coord.approx), td(c.boatSpeed), td(c.time.dateTime)),
              c.sentences.map { sentence =>
                tr(`class` := "row-sm")(
                  td(colspan := 2)(sentence.sentence),
                  td(sentence.time.dateTime)
                )
              }
            )
          }
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
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(track.boatName)
        )
      ),
      div(`class` := "mb-3")(
        form(
          `class` := Hidden,
          id := EditTitleFormId,
          method := "PUT",
          action := reverse.modifyTitle(track.trackName)
        )(
          div(`class` := "form-group row form-title")(
            label(`for` := TitleInputId, `class` := "col-sm-2 col-form-label col-form-label-sm")(
              webLang.editTitle
            ),
            div(`class` := "col-sm-7 pr-2")(
              input(
                `type` := "text",
                id := TitleInputId,
                name := TrackTitle.Key,
                `class` := "form-control form-control-sm input-title",
                track.trackTitle.map(t => value := t.title).getOrElse(modifier()),
                placeholder := webLang.titlePlaceholder
              )
            ),
            div(`class` := "col-3 pl-0")(
              button(`type` := "submit", `class` := "btn btn-sm btn-primary")(webLang.save),
              button(
                `type` := "button",
                id := CancelEditTrackId,
                `class` := "btn btn-sm btn-secondary ml-1"
              )(webLang.cancel)
            )
          )
        ),
        dl(`class` := "row mb-0")(
          dt(`class` := s"col-sm-2 $TrackRow")(trackLang.track),
          dd(`class` := s"col-sm-10 $TrackRow")(
            span(`class` := "text-editable", id := TrackTitleId)(track.describe),
            editIcon(EditTitleId, webLang.editTitle)
          ),
          description(lang.lang.time, track.times.range),
          description(trackLang.duration, BoatFormats.formatDuration(track.duration)),
          description(trackLang.topSpeed, topSpeed),
          dt(`class` := s"col-sm-2 $CommentsRow")(trackLang.comments),
          dd(`class` := s"col-sm-10 $CommentsRow")(
            span(
              `class` := classes("text-editable", track.comments.fold(Hidden)(_ => "")),
              id := CommentsTitleId
            )(commentValue),
            editIcon(EditCommentsId, webLang.editComments)
          )
        ),
        form(
          `class` := s"$Hidden mb-3",
          id := EditCommentsFormId,
          method := "PATCH",
          action := reverse.updateComments(track.track)
        )(
          div(`class` := "form-group row form-title")(
            label(`for` := CommentsInputId, `class` := "col-sm-2 col-form-label col-form-label-sm")(
              webLang.editComments
            ),
            div(`class` := "col-sm-7 pr-2")(
              input(
                `type` := "text",
                id := CommentsInputId,
                name := TrackComments.Key,
                `class` := "form-control form-control-sm input-title",
                track.comments.map(c => value := c).getOrElse(modifier()),
                placeholder := webLang.commentsPlaceholder
              )
            ),
            div(`class` := "col-3 pl-0")(
              button(`type` := "submit", `class` := "btn btn-sm btn-primary")(webLang.save),
              button(
                `type` := "button",
                id := CancelEditCommentsId,
                `class` := "btn btn-sm btn-secondary ml-1"
              )(webLang.cancel)
            )
          )
        )
      )
    )

  private def editIcon(editId: String, titleText: String) =
    span(
      id := editId,
      `class` := s"fas fa-edit icon-link edit-icon tight",
      title := titleText,
      aria.hidden := "true"
    )

  private def description(labelText: String, value: Modifier) = modifier(
    dt(`class` := "col-sm-2")(labelText),
    dd(`class` := "col-sm-10")(value)
  )

  private def pagination(track: TrackRef, current: Limits) =
    val pageSize = 100
    val trackName = track.trackName
    val hasMore = current.offset + pageSize < track.points
    val prevOffset = math.max(current.offset - pageSize, 0)
    val lastOffset = ((track.points - 1) / pageSize) * pageSize
    tag("nav")(aria.label := "Navigation")(
      ul(`class` := "pagination justify-content-center")(
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
    li(`class` := classes("page-item", liClass))(a(`class` := "page-link", href := call)(text))

  def classes(cs: String*) = cs.filter(_.nonEmpty).mkString(" ")
