package com.malliina.boat.db

import com.malliina.boat.UserToken
import com.malliina.values.{Email, Username}

case class NewUser(username: Username,
                   email: Option[Email],
                   token: UserToken,
                   enabled: Boolean)
