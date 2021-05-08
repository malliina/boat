package com.malliina.boat

import com.malliina.boat.FrontKeys.{CommentsRow, TrackRow}
import com.malliina.boat.http.CSRFConf
import com.malliina.http.HttpClient
import com.malliina.values.ErrorMessage
import org.scalajs.dom.raw._
import play.api.libs.json.{JsObject, Json}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

object FormHandlers extends BaseFront {
  def titles(): Either[NotFound, TitleHandler] = {
    for {
      form <- elemAs[HTMLFormElement](EditTitleFormId)
      editIcon <- elem(EditTitleId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditTrackId)
    } yield new TitleHandler(form, editIcon, cancelButton)
  }

  def comments(): Either[NotFound, CommentsHandler] =
    for {
      form <- elemAs[HTMLFormElement](EditCommentsFormId)
      editIcon <- elem(EditCommentsId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditCommentsId)
    } yield new CommentsHandler(form, editIcon, cancelButton)

  def invites() = for {
    parent <- elemsByClass[Element](FormParent)
    form <- parent.getElementsByClassName(InviteFormClass).map(_.asInstanceOf[HTMLFormElement])
    open <- parent.getElementsByClassName(InviteFormOpen).headOption
    cancel <- parent.getElementsByClassName(FormCancel).headOption
    delete <- parent.getElementsByClassName(DeleteForm).headOption
  } yield new InviteHandler(parent, form, open, cancel, delete)
}

class InviteHandler(
  parent: Element,
  form: HTMLFormElement,
  open: Element,
  cancel: Element,
  delete: Element,
  log: BaseLogger = BaseLogger.console
) extends BaseFront {
  form.onsubmit = (e: Event) => {
    for {
      email <- descendantValue(InviteFormInputClass)
      boat <- descendantValue(InviteFormBoatClass)
      boatNum <- Try(boat.toInt).toEither.left.map(t => ErrorMessage(t.getMessage))
    } yield HttpClient.post[JsObject, SimpleMessage](
      form.action,
      Json.obj(Emails.Key -> email, BoatIds.Key -> boatNum)
    )
    e.preventDefault()
  }

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

  def descendantValue(cls: String) =
    form
      .getElementsByClassName(cls)
      .headOption
      .map(_.asInstanceOf[HTMLInputElement])
      .map(_.value)
      .toRight(ErrorMessage(s"No element found with class '$cls'."))
}

class TitleHandler(
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, TrackRow, cancel)
  with CSRFConf {
  form.onsubmit = (e: Event) => {
    elemAs[HTMLInputElement](TitleInputId).map { in =>
      HttpClient
        .put[JsObject, TrackResponse](form.action, Json.obj(TrackTitle.Key -> in.value))
        .map { res =>
          elemAs[HTMLSpanElement](TrackTitleId).map { span =>
            span.textContent = res.track.describe
          }
          form.hide()
          editable.foreach(_.show())
        }
    }
    e.preventDefault()
  }
}

class CommentsHandler(
  form: HTMLFormElement,
  editIcon: Element,
  cancel: HTMLButtonElement,
  log: BaseLogger = BaseLogger.console
) extends AjaxForm(form, editIcon, CommentsRow, cancel)
  with CSRFConf {
  form.onsubmit = (e: Event) => {
    elemAs[HTMLInputElement](CommentsInputId).map { in =>
      HttpClient
        .patch[JsObject, TrackResponse](form.action, Json.obj(TrackComments.Key -> in.value))
        .map { res =>
          elemAs[HTMLSpanElement](CommentsTitleId).map { span =>
            val textContent = res.track.comments.getOrElse("")
            span.textContent = textContent
            if (textContent.nonEmpty)
              span.classList.remove(Hidden)
          }
          form.hide()
          editable.foreach(_.show())
        }
    }
    e.preventDefault()
  }
}

abstract class AjaxForm(
  form: HTMLFormElement,
  editIcon: Element,
  editableClass: String,
  cancel: HTMLButtonElement
) extends BaseFront {
  val editable = document.getElementsByClassName(editableClass)
  editIcon.addEventListener(
    "click",
    (_: Event) => {
      editable.foreach(_.hide())
      form.show()
    }
  )

  cancel.onclick = (_: MouseEvent) => {
    editable.foreach(_.show())
    form.hide()
  }
}
