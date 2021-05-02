package com.malliina.boat

import com.malliina.boat.FrontKeys.{CommentsRow, TrackRow}
import com.malliina.boat.http.CSRFConf
import com.malliina.http.HttpClient
import org.scalajs.dom.raw._
import play.api.libs.json.{JsObject, Json}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

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

//class InviteHandler(form: HTMLFormElement, editIcon: Element, cancel: HTMLButtonElement)
//  extends AjaxForm(form, editIcon, ???, cancel) {}

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
