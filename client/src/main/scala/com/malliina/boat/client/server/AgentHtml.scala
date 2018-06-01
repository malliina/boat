package com.malliina.boat.client.server

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import scalatags.Text

object AgentHtml {

  import scalatags.Text.all._

  val empty = stringFrag("")

  def boatForm(conf: BoatConf) = form(action := WebServer.settingsUri, method := "post")(
    h2("Settings"),
    div(`class` := "form-field")(
      label(`for` := "host")("Host"),
      input(`type` := "text", name := "host", id := "host", value := conf.host)
    ),
    div(`class` := "form-field")(
      label(`for` := "port")("Port"),
      input(`type` := "number", name := "port", id := "port", value := conf.port)
    ),
    div(`class` := "form-field")(
      label(`for` := "token")("Token"),
      input(`type` := "text", name := "token", id := "token", conf.token.map(v => value := v.token).getOrElse(empty))
    ),
    div(`class` := "form-field")(
      label(`for` := "enabled")("Enabled"),
      input(`type` := "checkbox", name := "enabled", id := "enabled", if (conf.enabled) checked else empty)
    ),
    div(`class` := "form-field")(
      button(`type` := "submit")("Save")
    )
  )

  def changePassForm = form(action := WebServer.changePassUri, method := "post")(
    h2("Set password"),
    div(`class` := "form-field")(
      label(`for` := "pass")("Password"),
      input(`type` := "password", name := "pass", id := "pass")
    ),
    div(`class` := "form-field")(
      button(`type` := "submit")("Save")
    )
  )

  def asHtml(content: Text.TypedTag[String]): HttpEntity.Strict = {
    val payload = html(
      head(link(rel := "stylesheet", href := "/css/boat.css")),
      body(content)
    )
    HttpEntity(ContentTypes.`text/html(UTF-8)`, payload.render)
  }
}
