package com.malliina.boat.html

import com.malliina.boat.FrontKeys.*
import com.malliina.boat.InviteState.{Accepted, Awaiting, Other, Rejected}
import com.malliina.boat.http.CSRFConf
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{BoatIds, BoatNames, BoatRef, Emails, Forms, InviteState, UserInfo}
import com.malliina.values.WrappedId
import scalatags.Text
import scalatags.Text.all.*
import scalatags.text.Builder

import scala.language.implicitConversions

object BoatsPage extends BoatImplicits with CSRFConf:
  val reverse = Reverse
  val empty: Modifier = modifier()

  def apply(user: UserInfo) =
    val langs = BoatLang(user.language)
    val lang = langs.lang
    val webLang = langs.web
    val settings = lang.settings
    val boatLang = settings.boatLang
    val inviteLang = settings.invite
    given Conversion[InviteState, Modifier] = {
      case Awaiting => inviteLang.awaiting
      case Accepted => inviteLang.accepted
      case Rejected => inviteLang.rejected
      case Other(_) => ""
    }
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
                      settings.invite.invite
                    )
                  )
                ),
                form(
                  method := "POST",
                  action := reverse.invites,
                  `class` := s"$InviteFormClass $Hidden"
                )(
                  div(`class` := "form-group row")(
                    hiddenInput(BoatIds.Key, boat.id),
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
                        settings.invite.invite
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
      form(method := "POST", action := reverse.createBoat, `class` := "pb-5")(
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
          h2(inviteLang.invites)
        )
      ),
      if user.invites.isEmpty then p(`class` := "mb-5")(inviteLang.noInvites)
      else
        table(`class` := "table table-hover mb-5")(
          thead(
            tr(
              th(boatLang.boat),
              th(inviteLang.state),
              th(settings.actions)
            )
          ),
          tbody(
            user.invites.map { i =>
              tr(
                td(i.boat.name),
                td(i.state),
                td(`class` := "table-button")(
                  div(`class` := "row")(
                    if i.state != Accepted then
                      respondForm(i.boat, inviteLang.accept, accept = true)
                    else empty,
                    if i.state != Rejected then
                      respondForm(i.boat, inviteLang.reject, accept = false)
                    else empty
                  )
                )
              )
            }
          )
        )
      ,
      div(`class` := "row")(
        div(`class` := "col-md-12")(
          h2(inviteLang.friends)
        )
      ),
      if user.friends.isEmpty then p(`class` := "mb-5")(inviteLang.noFriends)
      else
        table(`class` := "table table-hover")(
          thead(
            tr(
              th(boatLang.boat),
              th(inviteLang.email),
              th(inviteLang.state),
              th(settings.actions)
            )
          ),
          tbody(
            user.friends.map { f =>
              val confirmRevokeText = inviteLang.confirmRevoke(f.boat.name, f.friend.email)
              tr(
                td(f.boat.name),
                td(f.friend.email),
                td(f.state),
                td(`class` := "table-button")(
                  form(
                    method := "POST",
                    action := reverse.revoke,
                    onsubmit := s"return confirm('$confirmRevokeText');",
                    `class` := FriendsForm
                  )(
                    hiddenInput(Forms.Boat, f.boat.id),
                    hiddenInput(Forms.User, f.friend.id),
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

  private def respondForm(boat: BoatRef, buttonText: String, accept: Boolean) =
    div(`class` := "col")(
      form(
        method := "POST",
        action := reverse.invitesRespond
      )(
        hiddenInput(Forms.Boat, boat.id),
        hiddenInput(Forms.Accept, s"$accept"),
        button(`type` := "submit", `class` := "btn btn-sm btn-info")(
          buttonText
        )
      )
    )

  implicit def attrId[T <: WrappedId]: AttrValue[T] = (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(s"$v"))

  def hiddenInput[T: AttrValue](inputName: String, inputValue: T) = input(
    `type` := "hidden",
    name := inputName,
    value := inputValue
  )

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
