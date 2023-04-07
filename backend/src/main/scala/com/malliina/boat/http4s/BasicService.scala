package com.malliina.boat.http4s

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{IO, Sync}
import cats.implicits.*
import com.malliina.boat.Errors
import com.malliina.boat.db.{BoatNotFoundException, IdentityException, MissingCredentialsException}
import com.malliina.boat.http4s.BasicService.{log, noCache}
import com.malliina.util.AppLogger
import com.malliina.http.ResponseException
import com.malliina.web.{AuthException, WebAuthException}
import org.http4s.CacheDirective.*
import org.http4s.headers.{Location, `Content-Type`, `WWW-Authenticate`}
import org.http4s.*
import org.http4s.headers.{Accept, Location, `Cache-Control`}

import scala.concurrent.duration.FiniteDuration

object BasicService:
  private val log = AppLogger(getClass)

  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  def ranges(headers: Headers) = headers
    .get[Accept]
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))

class BasicService[F[_]: Sync] extends Implicits[F]:
  def temporaryRedirect(uri: Uri): F[Response[F]] =
    TemporaryRedirect(Location(uri)).map(_.putHeaders(noCache))
  def seeOther(uri: Uri): F[Response[F]] =
    SeeOther(Location(uri)).map(_.putHeaders(noCache))
  def ok[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    Ok(a, noCache)
  def badRequest[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    BadRequest(a, noCache)
  def notFoundReq(req: Request[F]): F[Response[F]] =
    notFound(Errors(s"Not found: '${req.uri}'."))
  def notFound[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    NotFound(a, noCache)
  def serverError[A](a: A)(implicit w: EntityEncoder[F, A]): F[Response[F]] =
    InternalServerError(a, noCache)
  def errorHandler(t: Throwable): F[Response[F]] = t match
    case ir: InvalidRequest =>
      Sync[F].delay(log.warn(ir.message, ir)).flatMap { _ =>
        badRequest(ir.errors)
      }
    case ie: IdentityException =>
      unauthorizedNoCache(Errors(ie.error.message))
    case ae: AuthException =>
      unauthorizedNoCache(Errors(ae.singleError))
    case bnfe: BoatNotFoundException =>
      Sync[F].delay(log.error(bnfe.message, t)).flatMap { _ =>
        notFound(Errors(bnfe.message))
      }
    case re: ResponseException =>
      serverErrorResponse(s"${re.getMessage} Response: '${re.response.asString}'.", re)
    case other =>
      serverErrorResponse("Server error.", other)

  private def serverErrorResponse(msg: String, t: Throwable) =
    Sync[F].delay(log.error(msg, t)).flatMap { _ =>
      serverError(Errors("Server error."))
    }

  def unauthorizedNoCache(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("myscheme", "myrealm"))),
      errors,
      noCache
    )
