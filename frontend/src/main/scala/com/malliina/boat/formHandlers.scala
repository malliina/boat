package com.malliina.boat

import cats.effect.Async
import cats.syntax.all.toFunctorOps
import com.malliina.boat.FrontKeys.{CommentsRow, TrackRow}
import com.malliina.http.Http
import io.circe.*
import io.circe.syntax.EncoderOps
import org.scalajs.dom.*

import scala.annotation.unused

class FormHandlers[F[_]: Async](http: Http[F]) extends BaseFront:
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

  def inviteOthers() =
    for
      parent <- elemsByClass[Element](FormParent)
      form <- parent.elementsByClass[HTMLFormElement](InviteFormClass)
      open <- parent.elementByClass(InviteFormOpen)
      cancel <- parent.elementByClass(FormCancel)
//      delete <- parent.elementByClass(DeleteForm)
    yield InviteHandler(form, open, cancel)

extension (e: Element)
  def elementByClass(cls: String): Option[Element] = e.getElementsByClassName(cls).headOption
  def elementsByClass[T <: Element](cls: String): List[T] =
    e.getElementsByClassName(cls).toList.map(_.asInstanceOf[T])
  def inputs: List[HTMLInputElement] = elementsByTag[HTMLInputElement]("input")
  def elementsByTag[T <: Element](cls: String): List[T] =
    e.getElementsByTagName(cls).toList.map(_.asInstanceOf[T])

class InviteHandler(
  form: HTMLFormElement,
  open: Element,
  cancel: Element
) extends BaseFront:
  open.addOnClick: _ =>
    form.show()
    open.hide()

  cancel.addOnClick: _ =>
    form.hide()
    open.show()

class TitleHandler[F[_]: Async](
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  http: Http[F],
  @unused log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, TrackRow, cancel):
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](TitleInputId).map: in =>
      http.using: client =>
        client
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
  http: Http[F]
) extends AjaxForm(form, editIcon, CommentsRow, cancel):
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](CommentsInputId).map: in =>
      http.using: client =>
        client
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
