package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{User, UserEmail, UserId}

case class DataUser(id: UserId, username: User, email: Option[UserEmail], passwordHash: String, enabled: Boolean, added: Instant)

case class NewUser(username: User, email: Option[UserEmail], passwordHash: String, enabled: Boolean)
