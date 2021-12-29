package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.auth.{BoatJwt, BoatJwtClaims, JWT}
import com.malliina.boat.db.UserManager
import com.malliina.values.*
import com.malliina.web.{AppleAuthFlow, AppleTokenValidator, Code}
import doobie.*
import doobie.implicits.*

import java.time.Instant
import scala.concurrent.duration.DurationInt

class SIWADatabase(
  siwa: AppleAuthFlow,
  users: TokenManager,
  jwt: JWT
) extends DoobieMappings:
  val tokenValidator = siwa.validator

  def register(code: Code, now: Instant): IO[BoatJwt] =
    for
      tokens <- siwa.refreshToken(code)
      email <- tokenValidator.validateOrFail(tokens.idToken, now)
      user <- users.register(email)
      saved <- users.save(tokens.refreshToken, user.id)
    yield
      val claims = BoatJwtClaims(email, saved)
      BoatJwt(claims.email, jwt.sign(claims, 3650.days, now))
