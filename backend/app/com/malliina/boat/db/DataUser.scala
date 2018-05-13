package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.UserId
import com.malliina.play.models.Username

case class DataUser(id: UserId, username: Username, passwordHash: String, enabled: Boolean, added: Instant)

case class NewUser(username: Username, passwordHash: String, enabled: Boolean)
