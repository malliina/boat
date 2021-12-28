package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.auth.JWTClaims
import com.malliina.boat.db.UserManager
import com.malliina.values.*
import com.malliina.web.{AppleAuthFlow, AppleTokenValidator, Code}
import doobie.*
import doobie.implicits.*

import java.time.Instant

class SIWADatabase(
  db: DoobieDatabase,
  siwa: AppleAuthFlow,
  tokenValidator: AppleTokenValidator,
  users: UserManager
) extends DoobieMappings:
  def register(code: Code, now: Instant): IO[JWTClaims] =
    for
      tokens <- siwa.refreshToken(code)
      email <- tokenValidator.validateOrFail(tokens.idToken, now)
      user <- users.register(email)
      saved <- save(tokens.refreshToken, user.id)
    yield JWTClaims(email, saved)

  def userBy(email: Email) = db.run {
    sql"""select id from users where email = $email""".query[UserId].option
  }

  def save(token: RefreshToken, user: UserId): IO[RefreshTokenId] = db.run {
    sql"""insert into refresh_tokens(refresh_token, owner) 
          values($token, $user)""".update.withUniqueGeneratedKeys[RefreshTokenId]("id")
  }

  def remove(token: RefreshTokenId) = db.run {
    sql"""delete from refresh_tokens where id = $token""".update.run
  }
