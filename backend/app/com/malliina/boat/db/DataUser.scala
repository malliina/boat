package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{User, UserEmail, UserId, UserToken}

case class DataUser(id: UserId,
                    username: User,
                    email: Option[UserEmail],
                    passwordHash: String,
                    token: UserToken,
                    enabled: Boolean,
                    added: Instant)

case class NewUser(username: User,
                   email: Option[UserEmail],
                   passwordHash: String,
                   token: UserToken,
                   enabled: Boolean)
