package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.UserToken
import com.malliina.values.{Email, UserId, Username}

case class DataUser(id: UserId,
                    username: Username,
                    email: Option[Email],
                    token: UserToken,
                    enabled: Boolean,
                    added: Instant)

case class NewUser(username: Username,
                   email: Option[Email],
                   token: UserToken,
                   enabled: Boolean)
