package com.malliina.boat

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

class TitleHandler(form: HTMLFormElement,
                   editIcon: Element,
                   cancel: HTMLButtonElement,
                   log: BaseLogger = BaseLogger.console)
    extends BaseFront
    with CSRFConf {
  val trackRow = document.getElementsByClassName(TrackRow)

  editIcon.addEventListener("click", (_: Event) => {
    trackRow.foreach(_.hide())
    form.show()
  })

  form.onsubmit = (e: Event) => {
    elemAs[HTMLInputElement](TitleInputId).map { in =>
      HttpClient
        .put[JsObject, TrackResponse](form.action, Json.obj(TrackTitle.Key -> in.value))
        .map { res =>
          elemAs[HTMLSpanElement](TrackTitleId).map { span =>
            span.textContent = res.track.describe
          }
          form.hide()
          trackRow.foreach(_.show())
        }
    }
    e.preventDefault()
  }

  cancel.onclick = (_: MouseEvent) => {
    trackRow.foreach(_.show())
    form.hide()
  }
}

class CommentsHandler(form: HTMLFormElement,
                      editIcon: Element,
                      cancel: HTMLButtonElement,
                      log: BaseLogger = BaseLogger.console)
    extends BaseFront
    with CSRFConf {
  val editRow = document.getElementsByClassName(CommentsRow)

  editIcon.addEventListener("click", (_: Event) => {
    editRow.foreach(_.hide())
    form.show()
  })
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
          editRow.foreach(_.show())
        }
    }
    e.preventDefault()
  }

  cancel.onclick = (_: MouseEvent) => {
    editRow.foreach(_.show())
    form.hide()
  }
}
