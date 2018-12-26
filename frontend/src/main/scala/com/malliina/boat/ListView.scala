package com.malliina.boat

import com.malliina.boat.http.CSRFConf
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw._
import play.api.libs.json.Json

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ListView extends BaseFront {
  def apply(): Either[NotFound, ListView] = {
    for {
      form <- elemAs[HTMLFormElement](EditTitleFormId)
      editIcon <- elem(EditTitleId)
      cancelButton <- elemAs[HTMLButtonElement](CancelEditTrackId)
    } yield new ListView(form, editIcon, cancelButton)
  }
}

class ListView(form: HTMLFormElement, editIcon: Element, cancel: HTMLButtonElement, log: BaseLogger = BaseLogger.console) extends BaseFront with CSRFConf {
  val trackRow = document.getElementsByClassName(TrackRow)

  editIcon.addEventListener("click", (_: Event) => {
    trackRow.foreach(_.hide())
    form.show()
  })

  form.onsubmit = (e: Event) => {
    for {
      track <- readTrack
      in <- elemAs[HTMLInputElement](TitleInputId)
    } {
      val data = Json.stringify(Json.obj(TrackTitle.Key -> in.value))
      val headers = Map("Content-Type" -> "application/json", CsrfHeaderName -> CsrfTokenNoCheck)
      Ajax.put(s"/tracks/$track", data, headers = headers).map { res =>
        if (res.status == 200) {
          for {
            json <- Json.parse(res.responseText).validate[TrackResponse].asEither
            e <- elemAs[HTMLSpanElement](TrackTitleId)
          } yield {
            e.textContent = json.track.describe
          }
        } else {
          log.info(s"Invalid status code '${res.status}'.")
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
