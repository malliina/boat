package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{User, UserId}

case class DataUser(id: UserId, username: User, passwordHash: String, enabled: Boolean, added: Instant)

case class NewUser(username: User, passwordHash: String, enabled: Boolean)
