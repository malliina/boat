package com.malliina.boat.client.server

import cats.Show
import com.malliina.boat.client.server.Device.{BoatDevice, GpsDevice}
import org.http4s.Uri
import scalatags.Text
import scalatags.Text.all
import scalatags.Text.all.*
import scalatags.text.Builder

object AgentHtml:
  val empty = stringFrag("")
  implicit val uriAttrValue: AttrValue[Uri] = attrValue[Uri](_.renderString)

  given showAttrValue[T](using s: Show[T]): AttrValue[T] =
    attrValue[T](v => s.show(v))

  given optShowAttrValue[T](using s: Show[T]): AttrValue[Option[T]] =
    attrValue[Option[T]](opt => opt.map(s.show).getOrElse(""))

  private def attrValue[T](f: T => String): AttrValue[T] =
    (t: Builder, a: Text.Attr, v: T) => t.setAttr(a.name, Builder.GenericAttrValueSource(f(v)))

  def boatForm(conf: Option[BoatConf]) =
    form(action := WebServer.settingsUri, method := "post")(
      h2("Settings"),
      div(`class` := "form-field")(
        label(`for` := "host")("Host"),
        input(
          `type` := "text",
          name := "host",
          id := "host",
          value := conf.map(_.host)
        )
      ),
      div(`class` := "form-field")(
        label(`for` := "port")("Port"),
        input(`type` := "number", name := "port", id := "port", value := conf.map(_.port))
      ),
      div(`class` := "form-field")(
        label(`for` := "device")("Device"),
        radioButton(
          "Boat",
          "device-boat",
          "device",
          BoatDevice.name,
          conf.forall(_.device == BoatDevice)
        ),
        radioButton(
          "GPS",
          "device-gps",
          "device",
          GpsDevice.name,
          conf.exists(_.device == GpsDevice)
        )
      ),
      div(`class` := "form-field")(
        label(`for` := "token")("Token"),
        input(
          `type` := "text",
          name := "token",
          id := "token",
          conf.flatMap(_.token).map(v => value := v).getOrElse(empty)
        )
      ),
      div(`class` := "form-field")(
        label(`for` := "enabled")("Enabled"),
        input(
          `type` := "checkbox",
          name := "enabled",
          id := "enabled",
          if conf.forall(_.enabled) then checked else empty
        )
      ),
      div(`class` := "form-field")(
        button(`type` := "submit")("Save")
      )
    )

  private def radioButton(
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
      if isChecked then checked else empty
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

  def asHtml(content: Modifier) =
    html(
      head(link(rel := "stylesheet", href := "/css/boat.css")),
      body(content)
    )
