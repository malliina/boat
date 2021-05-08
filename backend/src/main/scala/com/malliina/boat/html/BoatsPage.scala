package com.malliina.boat.html

import com.malliina.boat.FrontKeys.{DeleteForm, FormCancel, FormParent, Hidden, InviteFormBoatClass, InviteFormClass, InviteFormInputClass, InviteFormOpen}
import com.malliina.boat.http.CSRFConf
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{BoatIds, BoatNames, DeviceId, Emails, UserInfo}
import scalatags.Text
import scalatags.Text.all._

object BoatsPage extends BoatImplicits with CSRFConf {
  val reverse = Reverse

  def apply(user: UserInfo): Text.TypedTag[String] = {
    val langs = BoatLang(user.language)
    val lang = langs.lang
    val webLang = langs.web
    val settings = lang.settings
    val boatLang = settings.boatLang
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
            th(settings.actions)
          )
        ),
        tbody(
          user.boats.map { boat =>
            val confirmDeletionText = s"${settings.delete} ${boat.name}?"
            tr(
              td(boat.name),
              td(boat.token),
              td(`class` := s"table-button $FormParent")(
                div(
                  div(`class` := "row")(
                    div(`class` := "col")(
                      form(
                        method := "POST",
                        action := reverse.boatDelete(boat.id),
                        onsubmit := s"return confirm('$confirmDeletionText');",
                        `class` := DeleteForm
                      )(
                        button(`type` := "submit", `class` := "btn btn-sm btn-danger")(
                          settings.delete
                        )
                      )
                    ),
                    div(`class` := "col")(
                      button(`type` := "button", `class` := s"btn btn-sm btn-info $InviteFormOpen")(
                        settings.invite
                      )
                    )
                  )
                ),
                form(
                  method := "POST",
                  action := reverse.invites,
                  `class` := s"$InviteFormClass $Hidden"
                )(
                  div(`class` := "form-group row")(
                    input(
                      `type` := "hidden",
                      name := BoatIds.Key,
                      `class` := InviteFormBoatClass,
                      value := s"${boat.id}"
                    ),
                    labeledInput(
                      "Email",
                      "email-label",
                      Emails.Key,
                      "col-form-label col-form-label-sm col-sm-2",
                      s"form-control form-control sm $InviteFormInputClass",
                      "michael@email.com"
                    ),
                    div(`class` := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
                      button(`type` := "submit", `class` := "btn btn-sm btn-primary")(
                        settings.invite
                      ),
                      button(
                        `type` := "button",
                        id := "cancel-invite",
                        `class` := s"btn btn-sm btn-secondary ml-1 $FormCancel"
                      )(webLang.cancel)
                    )
                  )
                )
              )
            )
          }
        )
      ),
      form(method := "POST", action := reverse.createBoat)(
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
          )
        )
      ),
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h2("Invites")
        )
      ),
      table(`class` := "table table-hover")(
        thead(
          tr(
            th(boatLang.boat),
            th("State"),
            th(settings.actions)
          )
        ),
        tbody(
          user.invites.map { i =>
            tr(
              td(i.boat.name),
              td(i.state.name),
              td("todo")
            )
          }
        )
      ),
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h2("Friends")
        )
      ),
      table(`class` := "table table-hover")(
        thead(
          tr(
            th(boatLang.boat),
            th("User"),
            th("State"),
            th("Actions")
          )
        ),
        tbody(
          user.friends.map { f =>
            val confirmRevokeText = s"Revoke access to ${f.boat.name} from ${f.friend.email}?"
            tr(
              td(f.boat.name),
              td(f.friend.email),
              td(f.state.name),
              td(
                form(
                  method := "POST",
                  action := reverse.revoke,
                  onsubmit := s"return confirm('$confirmRevokeText');",
                  `class` := DeleteForm
                )(
                  button(`type` := "submit", `class` := "btn btn-sm btn-danger")(
                    settings.delete
                  )
                )
              )
            )
          }
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
        name := inputName
      )
    )
  )

//  def csrfInput(token: CSRF.Token) =
//    input(`type` := "hidden", name := token.name, value := token.value)
}
