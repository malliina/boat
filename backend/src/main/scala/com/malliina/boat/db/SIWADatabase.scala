package com.malliina.boat.db

import cats.effect.IO
import cats.implicits.*
import com.malliina.boat.auth.{BoatJwt, BoatJwtClaims, JWT, JWTException}
import com.malliina.boat.db.SIWADatabase.log
import com.malliina.boat.db.UserManager
import com.malliina.util.AppLogger
import com.malliina.values.*
import com.malliina.web
import com.malliina.web.{AppleAuthFlow, AppleTokenValidator, Code, Expired, InvalidClaims, JWTError}
import doobie.*
import doobie.implicits.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SIWADatabase:
  private val log = AppLogger(getClass)

class SIWADatabase(
  siwa: AppleAuthFlow,
  users: TokenManager,
  jwt: CustomJwt
) extends DoobieMappings:
  private val tokenValidator = siwa.validator

  def register(code: Code, now: Instant): IO[BoatJwt] =
    for
      tokens <- siwa.refreshToken(code)
      email <- tokenValidator.validateOrFail(tokens.idToken, now)
      user <- users.register(email)
      saved <- users.save(tokens.refreshToken, user.id)
    yield
      val claims = BoatJwtClaims(email, saved.id, saved.lastVerification)
      BoatJwt(claims.email, jwt.write(claims, now))

  /** Verifies the refresh token associated with the ID token, and returns a JWT.
    *
    * The client should use this JWT going forward.
    */
  def recreate(token: IdToken, now: Instant): IO[BoatJwt] =
    liftEither(jwt.verify(token, now)).flatMap { claims =>
      users.load(claims.refresh).flatMap { row =>
        if r.canVerify then
          for
            res <- siwa.verifyRefreshToken(row.token)
            _ = log.info(
              s"Verified refresh token with ID '${row.id}' for ${claims.email}. Last verification was at ${row.lastVerification}."
            )
            email <- tokenValidator.validateOrFail(res.id_token, now)
            _ <- IO.raiseWhen(claims.email != email) {
              val msg = ErrorMessage(
                s"Email in claims was '${claims.email}', expected '$email'. Token was '$token'."
              )
              JWTException(InvalidClaims(token, msg))
            }
            upd <- users.updateValidation(claims.refresh)
          yield claims.copy(lastValidation = upd.lastVerification)
        else IO.pure(claims)
      }
    }.map { cs =>
      BoatJwt(cs.email, jwt.write(cs, now))
    }

  private def liftEither[T](e: Either[JWTError, T]): IO[T] =
    e.fold(err => IO.raiseError(JWTException(err)), t => IO.pure(t))

class CustomJwt(jwt: JWT):
  val ttl: FiniteDuration = 3640.days

  def email(token: IdToken, now: Instant) = validate(token, now).map(_.email)

  def validate(token: IdToken, now: Instant): Either[JWTError, BoatJwtClaims] =
    verify(token, now).flatMap { claims =>
      val exp = claims.lastValidation.plus(2, ChronoUnit.DAYS)
      Either.cond(now.isBefore(exp), claims, Expired(token, exp, now))
    }

  def write(claims: BoatJwtClaims, now: Instant) = jwt.sign(claims, ttl, now)
  def verify(token: IdToken, now: Instant) = jwt.verify[BoatJwtClaims](token, now)
