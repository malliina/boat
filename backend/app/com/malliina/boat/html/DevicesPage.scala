package com.malliina.boat.html

import com.malliina.boat.http.CSRFConf
import com.malliina.boat.{BoatNames, UserInfo}
import controllers.routes
import play.filters.csrf.CSRF
import scalatags.Text
import scalatags.Text.all._

object DevicesPage extends BoatImplicits with CSRFConf {
  val reverse = routes.BoatController

  def apply(user: UserInfo, token: CSRF.Token): Text.TypedTag[String] = {
    val langs = BoatLang(user.language)
    val lang = langs.lang
    val webLang = langs.web
    val boatLang = lang.settings.boatLang
    div(`class` := "container")(
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h1(lang.track.boats)
        )
      ),
      table(`class` := "table table-hover")(
        thead(
          tr(
            th(boatLang.boat),
            th(boatLang.token),
            th(lang.settings.actions)
          )
        ),
        tbody(
          user.boats.map { boat =>
            tr(
              td(boat.name),
              td(boat.token),
              td(`class` := "table-button")(
                form(method := "POST", action := reverse.deleteBoat(boat.id))(
                  button(`type` := "submit", `class` := "btn btn-sm btn-danger")(
                    lang.settings.delete
                  ),
                  csrfInput(token)
                )
              )
            )
          }
        )
      ),
      form(method := "POST", action := reverse.createBoat())(
        div(`class` := "form-group row")(
          labeledInput(
            boatLang.addBoat,
            "boat-name-label",
            BoatNames.Key,
            "col-form-label col-form-label-sm col-sm-2",
            "form-control form-control-sm",
            "Titanic"
          ),
          div(`class` := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
            button(`type` := "submit", `class` := "btn btn-sm btn-primary")(webLang.save)
          ),
          csrfInput(token)
        )
      )
    )
  }

  def labeledInput(
    labelText: String,
    inputId: String,
    inputName: String,
    labelClass: String,
    inputClass: String,
    placeholderValue: String
  ) = modifier(
    label(`class` := labelClass, `for` := inputId)(labelText),
    div(`class` := "col-sm-7")(
      input(
        `type` := "text",
        id := inputId,
        `class` := inputClass,
        placeholder := placeholderValue,
        name := BoatNames.Key
      )
    )
  )

  def csrfInput(token: CSRF.Token) =
    input(`type` := "hidden", name := token.name, value := token.value)
}
