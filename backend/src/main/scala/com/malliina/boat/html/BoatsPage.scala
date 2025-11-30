package com.malliina.boat.html

import cats.implicits.toShow
import com.malliina.boat.FrontKeys.*
import com.malliina.boat.InviteState.{Accepted, Awaiting, Other, Rejected}
import com.malliina.http.{CSRFConf, CSRFToken}
import com.malliina.boat.http4s.Reverse
import com.malliina.boat.{Boat, BoatIds, BoatNames, BoatRef, CarSummary, Emails, Forms, GPSInfo, InviteState, Passwords, PolestarLang, SourceType, UserInfo, Usernames}
import com.malliina.measure.DistanceM
import com.malliina.values.WrappedId
import scalatags.Text.all.*
import scalatags.text.Builder

object HTMLConstants extends HTMLConstants

trait HTMLConstants:
  val post = "POST"
  val row = "row"

object BoatsPage extends BoatImplicits with HTMLConstants:
  val reverse = Reverse
  val empty: Modifier = modifier()

  given Conversion[DistanceM, Frag] = intDistanceKm

  def edit(user: UserInfo, boat: Boat, csrfToken: CSRFToken, csrfConf: CSRFConf) =
    val langs = BoatLang(user.language)
    val lang = langs.lang
    val settings = lang.settings
    val boatLang = settings.boatLang
    div(cls := "container")(
      div(cls := row)(
        div(cls := "col-md-12")(
          h1(lang.track.boats)
        )
      ),
      form(
        method := post,
        action := reverse.boatEdit(boat.id),
        cls := row
      )(
        input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
        hiddenInput(BoatIds.Key, boat.id),
        div(cls := "mb-3 col-md-5")(
          formInput(
            boatLang.ip,
            "ip-label",
            GPSInfo.IpKey,
            "form-label",
            "form-control",
            "192.168.0.1",
            inputValue = boat.gps.map(_.ip.show)
          )
        ),
        div(cls := "mb-3 col-md-5")(
          formInput(
            boatLang.port,
            "port-label",
            GPSInfo.PortKey,
            "form-label",
            "form-control",
            "10110",
            inputValue = boat.gps.map(gps => s"${gps.port}")
          )
        ),
        div(cls := "col-12")(
          button(`type` := "submit", cls := "btn btn-primary")(
            settings.done
          )
        )
      )
    )

  private def carSummary(car: CarSummary, plang: PolestarLang) =
    val infoLang = plang.info
    val telematicsFields = Seq[(String, Modifier)](
      infoLang.batteryPercentage -> s"${car.battery.chargeLevelPercentage}%",
      infoLang.estimatedRange -> car.battery.range,
      infoLang.odometer -> car.odometer.odometer,
      infoLang.daysToService -> car.health.daysToService
    )
    div(cls := s"$row col-xl-6 mb-3")(
      (Seq[(String, Modifier)](
        plang.registrationNumber -> car.registrationNumber,
        plang.vin -> car.vin,
        plang.modelYear -> car.modelYear,
        plang.softwareVersion -> car.softwareVersion,
        plang.interior -> car.interiorSpec,
        plang.exterior -> car.exteriorSpec
      ) ++ telematicsFields).map: (key, value) =>
        div(cls := "d-flex flex-column col-12 col-sm-6 col-md-4 text-center mb-2")(
          div(cls := "fw-semibold")(key),
          div(cls := "p-2")(value)
        )
    )

  def apply(
    user: UserInfo,
    cars: Seq[CarSummary],
    csrfToken: CSRFToken,
    csrfConf: CSRFConf
  ) =
    val langs = BoatLang(user.language)
    val lang = langs.lang
    val webLang = langs.web
    val settings = lang.settings
    val boatLang = settings.boatLang
    val carLang = settings.polestar
    val inviteLang = settings.invite
    given Conversion[InviteState, Modifier] =
      case Awaiting => inviteLang.awaiting
      case Accepted => inviteLang.accepted
      case Rejected => inviteLang.rejected
      case Other(_) => ""
    div(cls := "container")(
      div(cls := row)(
        div(cls := "col-md-12")(
          h1(lang.track.boats)
        )
      ),
      table(cls := "table table-hover mb-5")(
        thead(
          tr(
            th(boatLang.boat),
            th(lang.name),
            th(boatLang.token),
            th(settings.actions),
            th(inviteLang.invites)
          )
        ),
        tbody(
          user.boats.map: boat =>
            tr(
              tdMiddle(
                if boat.sourceType == SourceType.Vehicle then boatLang.car else boatLang.boat
              ),
              tdMiddle(boat.name),
              tdMiddle(boat.token),
              tdMiddle(a(href := reverse.boatEdit(boat.id), cls := "align-middle")(settings.edit)),
              td(cls := s"$row table-button $FormParent")(
                div(cls := "col")(
                  button(`type` := "button", cls := s"btn btn-sm btn-info $InviteFormOpen")(
                    settings.invite.invite
                  )
                ),
                form(
                  method := post,
                  action := reverse.invites,
                  cls := s"row $InviteFormClass $Hidden"
                )(
                  input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
                  hiddenInput(BoatIds.Key, boat.id),
                  labeledInput(
                    "Email",
                    "email-label",
                    Emails.Key,
                    "col-form-label col-form-label-sm col-sm-2",
                    s"form-control form-control-sm $InviteFormInputClass",
                    "michael@email.com",
                    inputType = "email"
                  ),
                  div(cls := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
                    button(`type` := "submit", cls := "btn btn-sm btn-primary")(
                      settings.invite.invite
                    ),
                    button(
                      `type` := "button",
                      id := "cancel-invite",
                      cls := s"btn btn-sm btn-secondary ms-2 $FormCancel"
                    )(webLang.cancel)
                  )
                )
              )
            )
        )
      ),
      div(cls := row)(
        div(cls := "col-md-12")(
          h2(boatLang.addBoat)
        )
      ),
      form(method := post, action := reverse.createBoat, cls := "row g-3 mb-5")(
        input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
        radio[SourceType](
          "source-boat",
          SourceType.Key,
          boatLang.boat,
          SourceType.Boat,
          isChecked = true
        ),
        radio[SourceType]("source-car", SourceType.Key, boatLang.car, SourceType.Vehicle, false),
        labeledInput(
          lang.name,
          "boat-name-label",
          BoatNames.Key,
          "col-form-label col-form-label-sm col-sm-2",
          "form-control form-control-sm",
          "Titanic"
        ),
        div(cls := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
          button(`type` := "submit", cls := "btn btn-sm btn-primary")(webLang.save)
        )
      ),
      div(cls := s"$row mb-3")(
        div(cls := "col-md-12")(
          h2(carLang.cars)
        )
      ),
      if cars.isEmpty then
        div(cls := s"$row mb-3")(
          p("No cars.")
        )
      else
        cars.map: car =>
          carSummary(car, lang.settings.polestar)
      ,
      div(cls := s"$row mb-3")(
        div(cls := "col-md-12")(
          h2(carLang.polestar)
        )
      ),
      form(method := post, action := reverse.createCar, cls := "row g-3 mb-5")(
        input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
        div(cls := s"$row mb-3")(
          labeledInput(
            carLang.username,
            "polestar-username-label",
            Usernames.Key,
            "col-form-label col-form-label-sm col-sm-3 col-lg-2",
            "form-control form-control-sm",
            "account@polestar.com",
            "email",
            inputDivClass = "col-sm-7 col-lg-4"
          )
        ),
        div(cls := s"$row mb-3")(
          labeledInput(
            carLang.password,
            "polestar-password-label",
            Passwords.Key,
            "col-form-label col-form-label-sm col-sm-3 col-lg-2",
            "form-control form-control-sm",
            "",
            "password",
            inputDivClass = "col-sm-7 col-lg-4"
          )
        ),
        div(cls := row)(
          div(cls := "col-sm-3 pl-sm-0 pt-2 pt-sm-0")(
            button(`type` := "submit", cls := "btn btn-sm btn-primary")(carLang.save)
          )
        )
      ),
      div(cls := row)(
        div(cls := "col-md-12")(
          h2(inviteLang.invites)
        )
      ),
      if user.invites.isEmpty then p(cls := "mb-5")(inviteLang.noInvites)
      else
        table(cls := "table table-hover mb-5")(
          thead(
            tr(
              th(boatLang.boat),
              th(inviteLang.state),
              th(settings.actions)
            )
          ),
          tbody(
            user.invites.map: i =>
              tr(
                td(i.boat.name),
                td(i.state),
                td(cls := "table-button")(
                  div(cls := row)(
                    if i.state != Accepted then
                      respondForm(i.boat, inviteLang.accept, accept = true, csrfToken, csrfConf)
                    else empty,
                    if i.state != Rejected then
                      respondForm(i.boat, inviteLang.reject, accept = false, csrfToken, csrfConf)
                    else empty
                  )
                )
              )
          )
        )
      ,
      div(cls := row)(
        div(cls := "col-md-12")(
          h2(inviteLang.friends)
        )
      ),
      if user.friends.isEmpty then p(cls := "mb-5")(inviteLang.noFriends)
      else
        table(cls := "table table-hover")(
          thead(
            tr(
              th(boatLang.boat),
              th(inviteLang.email),
              th(inviteLang.state),
              th(settings.actions)
            )
          ),
          tbody(
            user.friends.map: f =>
              val confirmRevokeText = inviteLang.confirmRevoke(f.boat.name, f.friend.email)
              tr(
                td(f.boat.name),
                td(f.friend.email),
                td(f.state),
                td(cls := "table-button")(
                  form(
                    method := post,
                    action := reverse.revoke,
                    onsubmit := s"return confirm('$confirmRevokeText');",
                    cls := FriendsForm
                  )(
                    input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
                    hiddenInput(Forms.Boat, f.boat.id),
                    hiddenInput(Forms.User, f.friend.id),
                    button(`type` := "submit", cls := "btn btn-sm btn-danger")(
                      settings.delete
                    )
                  )
                )
              )
          )
        )
    )

  private def tdMiddle = td(cls := "align-middle")

  private def radio[T: AttrValue](
    inputId: String,
    inputName: String,
    text: String,
    inputValue: T,
    isChecked: Boolean
  ) =
    div(cls := "form-check mx-2")(
      input(
        cls := "form-check-input",
        tpe := "radio",
        name := inputName,
        id := inputId,
        value := inputValue,
        if isChecked then checked else empty
      ),
      label(cls := "form-check-label", `for` := inputId)(text)
    )

  private def respondForm(
    boat: BoatRef,
    buttonText: String,
    accept: Boolean,
    csrfToken: CSRFToken,
    csrfConf: CSRFConf
  ) =
    div(cls := "col")(
      form(
        method := post,
        action := reverse.invitesRespond
      )(
        input(tpe := "hidden", name := csrfConf.tokenName, value := csrfToken),
        hiddenInput(Forms.Boat, boat.id),
        hiddenInput(Forms.Accept, s"$accept"),
        button(`type` := "submit", cls := "btn btn-sm btn-info")(
          buttonText
        )
      )
    )

  given attrId: [T <: WrappedId] => AttrValue[T] = (t: Builder, a: Attr, v: T) =>
    t.setAttr(a.name, Builder.GenericAttrValueSource(s"$v"))

  private def hiddenInput[T: AttrValue](inputName: String, inputValue: T) = input(
    `type` := "hidden",
    name := inputName,
    value := inputValue
  )

  private def labeledInput(
    labelText: String,
    inputId: String,
    inputName: String,
    labelClass: String,
    inputClass: String,
    placeholderValue: String,
    inputType: String = "text",
    inputDivClass: String = "col-sm-7"
  ) = modifier(
    label(cls := labelClass, `for` := inputId)(labelText),
    div(cls := inputDivClass)(
      input(
        `type` := inputType,
        id := inputId,
        cls := inputClass,
        placeholder := placeholderValue,
        name := inputName
      )
    )
  )

  private def formInput(
    labelText: String,
    inputId: String,
    inputName: String,
    labelClass: String,
    inputClass: String,
    placeholderValue: String,
    inputValue: Option[String],
    inputType: String = "text"
  ) = modifier(
    label(cls := labelClass, `for` := inputId)(labelText),
    input(
      tpe := inputType,
      id := inputId,
      cls := inputClass,
      placeholder := placeholderValue,
      name := inputName,
      inputValue.fold(empty)(v => value := v)
    )
  )
