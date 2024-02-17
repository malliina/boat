package com.malliina.boat

import cats.effect.Async
import cats.syntax.all.toFunctorOps
import com.malliina.boat.FrontKeys.{CommentsRow, TrackRow}
import com.malliina.boat.http.CSRFConf
import com.malliina.http.HttpClient
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.*

import scala.annotation.unused

class FormHandlers[F[_]: Async](http: HttpClient[F]) extends BaseFront:
  def titles(): Either[NotFound, TitleHandler[F]] =
    for
      form <- elemAs[HTMLFormElement](EditTitleFormId)
      editIcon <- elem(EditTitleId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditTrackId)
    yield TitleHandler(form, editIcon, cancelButton, http)

  def comments(): Either[NotFound, CommentsHandler[F]] =
    for
      form <- elemAs[HTMLFormElement](EditCommentsFormId)
      editIcon <- elem(EditCommentsId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditCommentsId)
    yield CommentsHandler(form, editIcon, cancelButton, http)

  def inviteOthers() = for
    parent <- elemsByClass[Element](FormParent)
    form <- parent.getElementsByClassName(InviteFormClass).map(_.asInstanceOf[HTMLFormElement])
    open <- parent.getElementsByClassName(InviteFormOpen).headOption
    cancel <- parent.getElementsByClassName(FormCancel).headOption
    delete <- parent.getElementsByClassName(DeleteForm).headOption
  yield InviteHandler(form, open, cancel, delete)

class InviteHandler(
  form: HTMLFormElement,
  open: Element,
  cancel: Element,
  delete: Element,
  @unused log: BaseLogger = BaseLogger.console
) extends BaseFront:
  open.addOnClick: _ =>
    form.show()
    open.hide()
    delete.hide()

  cancel.addOnClick: _ =>
    form.hide()
    open.show()
    delete.show()

class TitleHandler[F[_]: Async](
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  http: HttpClient[F],
  @unused log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, TrackRow, cancel)
  with CSRFConf:
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](TitleInputId).map: in =>
      http
        .put[Json, TrackResponse](form.action, Json.obj(TrackTitle.Key -> in.value.asJson))
        .map: res =>
          elemAs[HTMLSpanElement](TrackTitleId).map: span =>
            span.textContent = res.track.describe
          form.hide()
          editable.foreach(_.show())
    e.preventDefault()

class CommentsHandler[F[_]: Async](
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  http: HttpClient[F],
  @unused log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, CommentsRow, cancel)
  with CSRFConf:
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](CommentsInputId).map: in =>
      http
        .patch[Json, TrackResponse](form.action, Json.obj(TrackComments.Key -> in.value.asJson))
        .map: res =>
          elemAs[HTMLSpanElement](CommentsTitleId).map: span =>
            val textContent = res.track.comments.getOrElse("")
            span.textContent = textContent
            if textContent.nonEmpty then span.classList.remove(Hidden)
          form.hide()
          editable.foreach(_.show())
    e.preventDefault()

abstract class AjaxForm(
  form: HTMLFormElement,
  editIcon: Element,
  editableClass: String,
  cancel: HTMLButtonElement
) extends BaseFront:
  val editable = document.getElementsByClassName(editableClass)
  editIcon.addEventListener(
    "click",
    (_: Event) =>
      editable.foreach(_.hide())
      form.show()
  )

  cancel.onclick = (_: MouseEvent) =>
    editable.foreach(_.show())
    form.hide()
