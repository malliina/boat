package com.malliina.boat

import com.malliina.boat.FrontKeys.{CommentsRow, TrackRow}
import com.malliina.boat.http.CSRFConf
import com.malliina.http.HttpClient
import org.scalajs.dom.*
import io.circe.*
import io.circe.syntax.EncoderOps

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object FormHandlers extends BaseFront:
  def titles(): Either[NotFound, TitleHandler] =
    for
      form <- elemAs[HTMLFormElement](EditTitleFormId)
      editIcon <- elem(EditTitleId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditTrackId)
    yield new TitleHandler(form, editIcon, cancelButton)

  def comments(): Either[NotFound, CommentsHandler] =
    for
      form <- elemAs[HTMLFormElement](EditCommentsFormId)
      editIcon <- elem(EditCommentsId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditCommentsId)
    yield new CommentsHandler(form, editIcon, cancelButton)

  def inviteOthers() = for
    parent <- elemsByClass[Element](FormParent)
    form <- parent.getElementsByClassName(InviteFormClass).map(_.asInstanceOf[HTMLFormElement])
    open <- parent.getElementsByClassName(InviteFormOpen).headOption
    cancel <- parent.getElementsByClassName(FormCancel).headOption
    delete <- parent.getElementsByClassName(DeleteForm).headOption
  yield new InviteHandler(form, open, cancel, delete)

class InviteHandler(
  form: HTMLFormElement,
  open: Element,
  cancel: Element,
  delete: Element,
  log: BaseLogger = BaseLogger.console
) extends BaseFront:
  open.addOnClick { e =>
    form.show()
    open.hide()
    delete.hide()
  }

  cancel.addOnClick { e =>
    form.hide()
    open.show()
    delete.show()
  }

class TitleHandler(
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, TrackRow, cancel)
  with CSRFConf:
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](TitleInputId).map { in =>
      HttpClient
        .put[Json, TrackResponse](form.action, Json.obj(TrackTitle.Key -> in.value.asJson))
        .map { res =>
          elemAs[HTMLSpanElement](TrackTitleId).map { span =>
            span.textContent = res.track.describe
          }
          form.hide()
          editable.foreach(_.show())
        }
    }
    e.preventDefault()

class CommentsHandler(
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, CommentsRow, cancel)
  with CSRFConf:
  form.onsubmit = (e: Event) =>
    elemAs[HTMLInputElement](CommentsInputId).map { in =>
      HttpClient
        .patch[Json, TrackResponse](form.action, Json.obj(TrackComments.Key -> in.value.asJson))
        .map { res =>
          elemAs[HTMLSpanElement](CommentsTitleId).map { span =>
            val textContent = res.track.comments.getOrElse("")
            span.textContent = textContent
            if textContent.nonEmpty then span.classList.remove(Hidden)
          }
          form.hide()
          editable.foreach(_.show())
        }
    }
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
