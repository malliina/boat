package com.malliina.boat.client.server

import com.malliina.boat.client.server.Device.{BoatDevice, GpsDevice}
import org.http4s.Uri
import scalatags.Text
import scalatags.Text.all._
import scalatags.text.Builder

object AgentHtml {
  val empty = stringFrag("")
  implicit val uriAttrValue: AttrValue[Uri] = attrValue[Uri](_.renderString)

  def attrValue[T](f: T => String): AttrValue[T] =
    (t: Builder, a: Text.Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(f(v)))

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
      label(`for` := "device")("Device"),
      radioButton("Boat", "device-boat", "device", BoatDevice.name, conf.device == BoatDevice),
      radioButton("GPS", "device-gps", "device", GpsDevice.name, conf.device == GpsDevice)
    ),
    div(`class` := "form-field")(
      label(`for` := "token")("Token"),
      input(
        `type` := "text",
        name := "token",
        id := "token",
        conf.token.map(v => value := v.token).getOrElse(empty)
      )
    ),
    div(`class` := "form-field")(
      label(`for` := "enabled")("Enabled"),
      input(
        `type` := "checkbox",
        name := "enabled",
        id := "enabled",
        if (conf.enabled) checked else empty
      )
    ),
    div(`class` := "form-field")(
      button(`type` := "submit")("Save")
    )
  )

  def radioButton(
    text: String,
    radioId: String,
    group: String,
    radioValue: String,
    isChecked: Boolean
  ) = div(
    input(
      `type` := "radio",
      name := group,
      id := radioId,
      value := radioValue,
      if (isChecked) checked else empty
    ),
    label(`for` := radioId)(text)
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

  def asHtml(content: Text.TypedTag[String]): Text.TypedTag[String] =
    html(
      head(link(rel := "stylesheet", href := "/css/boat.css")),
      body(content)
    )
}
