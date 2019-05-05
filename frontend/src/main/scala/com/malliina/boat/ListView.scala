package com.malliina.boat

import com.malliina.boat.http.CSRFConf
import com.malliina.http.HttpClient
import org.scalajs.dom.raw._
import play.api.libs.json.{JsObject, Json}

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

class ListView(form: HTMLFormElement,
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
      readTrack.toOption.map { track =>
        HttpClient.put[JsObject, TrackResponse](s"/tracks/$track", Json.obj(TrackTitle.Key -> in.value)).map {
          res =>
            elemAs[HTMLSpanElement](TrackTitleId).map { span =>
              span.textContent = res.track.describe
            }
            form.hide()
            trackRow.foreach(_.show())
        }
      }
    }
    e.preventDefault()
  }

  cancel.onclick = (_: MouseEvent) => {
    trackRow.foreach(_.show())
    form.hide()
  }
}
