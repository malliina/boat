package com.malliina.boat.db

import com.malliina.boat.UserToken
import com.malliina.values.{Email, Username}
import io.getquill.Embedded

case class NewUser(user: Username, email: Option[Email], token: UserToken, enabled: Boolean)
  extends Embedded

object NewUser {
  def email(email: Email): NewUser =
    NewUser(Username(email.email), Option(email), UserToken.random(), enabled = true)
}
