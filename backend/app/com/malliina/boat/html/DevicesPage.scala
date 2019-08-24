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
            th(boatLang.token)
          )
        ),
        tbody(
          user.boats.map { boat =>
            tr(
              td(boat.name),
              td(boat.token)
            )
          }
        )
      ),
      form(method := "POST", action := reverse.createBoat())(
        div(`class` := "form-group row")(
          label(`class` := "col-sm-2", `for` := "boat-name-label")(boatLang.addBoat),
          div(`class` := "col-sm-7")(
            input(`type` := "text",
                  id := "boat-name-label",
                  `class` := "form-control form-control-sm",
                  placeholder := "Titanic",
                  name := BoatNames.Key),
            csrfInput(token.name, token.value)
          ),
          div(`class` := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
            button(`type` := "submit", `class` := "btn btn-sm btn-primary")(webLang.save)
          )
        )
      )
    )
  }

  def csrfInput(inputName: String, inputValue: String) =
    input(`type` := "hidden", name := inputName, value := inputValue)
}
