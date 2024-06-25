package com.malliina.boat.http4s

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits.*
import com.malliina.boat.db.{BoatNotFoundException, IdentityException}
import com.malliina.boat.http4s.BasicService.{log, noCache}
import com.malliina.boat.{Errors, message}
import com.malliina.http.ResponseException
import com.malliina.util.AppLogger
import com.malliina.web.AuthException
import org.http4s.*
import org.http4s.CacheDirective.*
import org.http4s.headers.*

import java.io.IOException
import scala.concurrent.duration.FiniteDuration

object BasicService:
  private val log = AppLogger(getClass)

  val noisyErrorMessage = "The specified network name is no longer available"

  val noCache = `Cache-Control`(`no-cache`(), `no-store`, `must-revalidate`)

  def cached(duration: FiniteDuration) = `Cache-Control`(
    NonEmptyList.of(`max-age`(duration), `public`)
  )

  def ranges(headers: Headers) = headers
    .get[Accept]
    .map(_.values.map(_.mediaRange))
    .getOrElse(NonEmptyList.of(MediaRange.`*/*`))

class BasicService[F[_]: Sync] extends Implicits[F]:
  val F = Sync[F]
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
      F
        .delay(log.warn(ir.message, ir))
        .flatMap: _ =>
          badRequest(ir.errors)
    case ie: IdentityException =>
      unauthorizedNoCache(Errors(ie.error.message))
    case ae: AuthException =>
      unauthorizedNoCache(Errors(ae.singleError))
    case bnfe: BoatNotFoundException =>
      F.delay(log.error(bnfe.message, t)).flatMap(_ => notFound(Errors(bnfe.message)))
    case re: ResponseException =>
      serverErrorResponse(s"${re.getMessage} Response: '${re.response.asString}'.", re)
    case ioe: IOException if ioe.message.exists(_.startsWith(BasicService.noisyErrorMessage)) =>
      serverError(Errors("Service IO error."))
    case other =>
      serverErrorResponse(s"Service error: '${other.getMessage}'.", other)

  private def serverErrorResponse(msg: String, t: Throwable) =
    F
      .delay(log.error(msg, t))
      .flatMap: _ =>
        serverError(Errors(s"Server error: '${t.getMessage}'."))

  def unauthorizedNoCache(errors: Errors) =
    Unauthorized(
      `WWW-Authenticate`(NonEmptyList.of(Challenge("Bearer", "Social login"))),
      errors,
      noCache
    )
